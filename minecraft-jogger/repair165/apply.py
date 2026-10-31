#!/usr/bin/env python3
"""Apply Minecraft Jogger 1.6.5 after 1.6.4: native-only camera routing and classified gestures."""
from pathlib import Path
import sys,shutil
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
java=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic'
file=java/'ClassicOverlay.java'
s=file.read_text()

def edit(old,new):
    global s
    count=s.count(old)
    if count!=1: raise RuntimeError('1.6.5 expected ONE overlay anchor; found %d: %r'%(count,old[:160]))
    s=s.replace(old,new,1)

# Camera is controlled by the launcher. A status packet from the injected
# target-classification agent must never flip PLAY to SCREEN while GLFW still
# has its native mouse grab, as happens around fast block-place actions.
edit('''            net.kdt.pojavlaunch.jogger.mobile.MinecraftKeyboard.gameState(state);
            overlay.raySupported=(capability&1)!=0;
            if(overlay.capabilities!=capability){overlay.capabilities=capability;overlay.geometryGui=-1;overlay.invalidate();}
            if (!state.equals(overlay.mode)) { overlay.release(); overlay.mode=state; overlay.invalidate(); }''',
     '''            String effective=TouchRoutingPolicy.effectiveMode(CallbackBridge.isGrabbing(),state,overlay.mode);
            net.kdt.pojavlaunch.jogger.mobile.MinecraftKeyboard.gameState(effective);
            overlay.raySupported=(capability&1)!=0;
            if(overlay.capabilities!=capability){overlay.capabilities=capability;overlay.geometryGui=-1;overlay.invalidate();}
            if (!effective.equals(overlay.mode)) { overlay.release(); overlay.mode=effective; overlay.invalidate(); }''')
edit('''            if (stopped) return; release();
            if (grabbed) mode="PLAY"; else if (mode.equals("PLAY")) mode="SCREEN";''',
     '''            if(stopped||!TouchRoutingPolicy.acceptGrabCallback(grabbed,CallbackBridge.isGrabbing()))return;
            if(!TouchRoutingPolicy.isNewGrabState(grabbed,mode))return;
            release();
            if (grabbed) mode="PLAY"; else if (mode.equals("PLAY")) mode="SCREEN";''')
edit('private boolean playing() { return CallbackBridge.isGrabbing() && mode.equals("PLAY"); }',
     'private boolean playing() { return !stopped && CallbackBridge.isGrabbing(); }')

# The old 8dp dead zone made almost every short view drag a no-op. Distinguish
# one-time activation from cumulative motion so the initial 3dp is not lost.
edit('private float lookX, lookY;', 'private float lookX, lookY;\n    private boolean cameraTurning, lookMoved;')
edit('gesture.begin(x,y,event.getEventTime(),dp(8));target(x,y,true);handler.postDelayed(hold,holdDelay());',
     '''gesture.begin(x,y,event.getEventTime(),dp(TouchRoutingPolicy.cameraSlopDp()));
                cameraTurning=false;lookMoved=false;
                target(x,y,true);
                pendingProbe=++tapSequence;probeHit=-1;
                if(!JoggerTouchBridge.requestTap(pendingProbe))pendingProbe=-1;
                handler.postDelayed(hold,holdDelay());''')
edit('float dx=(x-gesture.lastX)*factor,dy=(y-gesture.lastY)*factor;',
     '''float dx=(x-(cameraTurning?gesture.lastX:gesture.startX))*factor;
                        float dy=(y-(cameraTurning?gesture.lastY:gesture.startY))*factor;''')
edit('''                        CallbackBridge.mouseX+=dx;CallbackBridge.mouseY+=dy;CallbackBridge.deltaX=dx;CallbackBridge.deltaY=dy;
                        CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY);''',
     '''                        if(dx!=0||dy!=0){
                            CallbackBridge.mouseX+=dx;CallbackBridge.mouseY+=dy;
                            CallbackBridge.deltaX=dx;CallbackBridge.deltaY=dy;
                            CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY);
                        }
                        cameraTurning=true;''')
# Keep a two-finger camera swipe from accidentally placing when the primary
# finger is raised a fraction before its own long-press threshold.
edit('''                    lookX=x;lookY=y;
                }else if(directional(route.button)) {''',
     '''                    if(dx!=0||dy!=0)lookMoved=true;
                    lookX=x;lookY=y;
                }else if(directional(route.button)) {''')

# Do not attack first and ask Minecraft what is under the finger later.
# Prefetch classification as soon as the target is sent on DOWN; the pending
# request ID is reused at the hold/tap moment if the reply is still in flight.
edit('private long pendingTap=-1,pendingGeneration,pendingHold=-1;',
     'private long pendingTap=-1,pendingGeneration,pendingHold=-1,pendingProbe=-1;\n    private int probeHit=-1;')
edit('''            // Classification happens on Minecraft's thread using the touched ray.
            // On MISS hold the use key, so food and chargeable items work naturally.
            pendingHold=++tapSequence;
            long request=pendingHold;
            // Start left-button mining at the HOLD threshold, not up to
            // 350ms later. A classified air MISS may switch to Use Item,
            // but a disconnected or unsupported ray must still be playable.
            inputs.set(-3,-1);
            if(JoggerTouchBridge.requestTap(request))handler.postDelayed(()->finishHold(request,1),350);
            else finishHold(request,1);
            invalidate();''',
     '''            // Use the game's actual ENTITY/BLOCK/MISS hit classification.
            // Never press attack before classification; doing so punches mobs
            // when the user intends to hold an item in empty space.
            if(pendingProbe>=0){
                pendingHold=pendingProbe;
                if(probeHit>=0)finishHold(pendingHold,probeHit);
                else {
                    final long request=pendingHold;
                    handler.postDelayed(()->finishHold(request,TouchRoutingPolicy.holdFallbackHit(raySupported)),350);
                }
            }else{
                pendingHold=++tapSequence;
                final long request=pendingHold;
                if(JoggerTouchBridge.requestTap(request))
                    handler.postDelayed(()->finishHold(request,TouchRoutingPolicy.holdFallbackHit(raySupported)),350);
                else finishHold(request,TouchRoutingPolicy.holdFallbackHit(raySupported));
            }
            invalidate();''')
# The pre-hold swipe cancels input and clears the ray; AFTER a mining/use hold
# starts the classifier may still be pending, so retain target and DOWN state.
edit('''                        pendingHold=-1;  // invalidate any delayed classification reply
                        if (!MiningDragPolicy.keepAttackWhileLooking(gesture.mining,inputs.held(-1))) {
                            inputs.set(-3);  // before a long hold, ordinary look swipes cancel taps
                            target(0,0,false);
                        }''',
     '''                        if(!gesture.mining){
                            pendingHold=-1;pendingProbe=-1;
                            inputs.set(-3);  // ordinary camera swipe cancels an unfinished tap
                            target(0,0,false);
                        }
                        // Once a hold starts, view changes never cancel either
                        // a pending hit reply or a held item/attack button.''')
edit('''                    handler.removeCallbacks(hold);boolean place=gesture.end(event.getEventTime(),holdDelay(),!playing());
                    pendingHold=-1;inputs.set(-3);worldPointer=-1;long generation=gestureGeneration;
                    if(!inputs.held(-1))JoggerTouchBridge.forceAttackRelease();
                    if(place) {
                        pendingTap=++tapSequence;pendingGeneration=generation;
                        boolean sent=JoggerTouchBridge.requestTap(pendingTap);
                        long request=pendingTap;
                        if(sent)handler.postDelayed(()->finishTap(request,1),350);else finishTap(request,1);
                    } else handler.postDelayed(() -> {if(gestureGeneration==generation&&worldPointer<0)target(0,0,false);},100);''',
     '''                    handler.removeCallbacks(hold);
                    boolean place=gesture.end(event.getEventTime(),holdDelay(),!playing()||lookMoved);
                    pendingHold=-1;inputs.set(-3);worldPointer=-1;
                    cameraTurning=false;long generation=gestureGeneration;
                    if(!inputs.held(-1))JoggerTouchBridge.forceAttackRelease();
                    if(place) {
                        pendingTap=pendingProbe>=0?pendingProbe:++tapSequence;
                        pendingGeneration=generation;
                        final long request=pendingTap;
                        if(probeHit>=0)finishTap(request,probeHit);
                        else if(pendingProbe>=0)
                            handler.postDelayed(()->finishTap(request,1),350);
                        else if(JoggerTouchBridge.requestTap(request))
                            handler.postDelayed(()->finishTap(request,1),350);
                        else finishTap(request,1);
                    }else {pendingProbe=-1;
                        handler.postDelayed(()->{if(gestureGeneration==generation&&worldPointer<0)target(0,0,false);},100);
                    }
                    // A lost agent response must never leave a targeting ray
                    // active forever after placing or attacking a block.
                    handler.postDelayed(()->{if(gestureGeneration==generation&&worldPointer<0)target(0,0,false);},480);''')
edit('''            if(request==overlay.pendingHold)overlay.finishHold(request,hit);
            else overlay.finishTap(request,hit);''',
     '''            if(request==overlay.pendingProbe){
                overlay.probeHit=hit;
                if(request==overlay.pendingHold)overlay.finishHold(request,hit);
                else if(request==overlay.pendingTap)overlay.finishTap(request,hit);
            }else if(request==overlay.pendingHold)overlay.finishHold(request,hit);
            else if(request==overlay.pendingTap)overlay.finishTap(request,hit);''')
edit('''        if(request!=pendingHold||stopped||!playing()||worldPointer<0||!gesture.active||!gesture.mining||gesture.moved)return;
        pendingHold=-1;
        if(!MiningLookPolicy.applyDelayedClassification(gesture.moved))return;
            inputs.set(-3,ClassicLogic.TouchAction.hold(
                !raySupported && hit==2 ? 1 : hit));''',
     '''        if(request!=pendingHold||stopped||!playing()||worldPointer<0||!gesture.active||!gesture.mining)return;
        pendingHold=-1;pendingProbe=-1;
        inputs.set(-3,ClassicLogic.TouchAction.hold(hit));''')
edit('''        pendingTap=-1;pulse(ClassicLogic.TouchAction.tap(button));long generation=gestureGeneration;''',
     '''        pendingTap=-1;pendingProbe=-1;
        int code=ClassicLogic.TouchAction.tap(button);
        if(code!=0)pulse(code);
        long generation=gestureGeneration;''')
edit('''handler.removeCallbacks(hold);inventoryTouch.cancel();pendingTap=-1;pendingHold=-1;gestureGeneration++;gesture.cancel();worldPointer=-1;lookPointer=-1;lastSlot=-1;''',
     '''handler.removeCallbacks(hold);inventoryTouch.cancel();pendingTap=-1;pendingHold=-1;pendingProbe=-1;probeHit=-1;
        gestureGeneration++;gesture.cancel();worldPointer=-1;lookPointer=-1;cameraTurning=false;lookMoved=false;lastSlot=-1;''')

assert s.count('CallbackBridge.sendCursorPos(')==2,'Two launch-only look routes must remain'
assert s.count('target(x,y,true);')==1
assert 'TouchRoutingPolicy.effectiveMode(CallbackBridge.isGrabbing(),state,overlay.mode)' in s
assert 'pendingHold=-1;pendingProbe=-1;' in s
assert 'inputs.set(-3,-1);' not in s[s.index('private final Runnable hold'):s.index('private static final class Button')]
assert 'JoggerTouchBridge.forceAttackRelease()' in s
assert 'MinecraftKeyboard.toggleManual()' in s
file.write_text(s)

logic=java/'ClassicLogic.java'
code=logic.read_text()
a='public static int tap(int hit) { return hit==0 ? -1 : -2; }'
b='public static int hold(int hit) { return hit==2 ? -2 : -1; }'
if code.count(a)!=1 or code.count(b)!=1:raise RuntimeError('Unexpected previous touch action semantics')
code=code.replace(a,'public static int tap(int hit) { return hit==0 ? -1 : hit==1 ? -2 : 0; }',1)
code=code.replace(b,'public static int hold(int hit) { return hit==1 ? -1 : -2; }',1)
logic.write_text(code)
shutil.copyfile(here/'TouchRoutingPolicy.java',java/'TouchRoutingPolicy.java')

# Keep the inherited regression suite meaningful: assert the new, safer
# semantics rather than demanding the superseded eager-attack workaround.
def test_replace(rel,old,new):
    p=root/rel
    src=p.read_text()
    count=src.count(old)
    if count!=1:raise RuntimeError(f'Unexpected legacy test {rel}: {count} matches for {old[:100]!r}')
    p.write_text(src.replace(old,new,1))

# Historical repair160 source test is outside reconstructed Android tree;
# its updated expectations are built by the CI runner, not here.
touchtest='scripts/mobile15-tests/TouchActionRegression.java'
test_replace(touchtest,'eq(ClassicLogic.TouchAction.tap(2),-2,"tap on air uses held item");',
    'eq(ClassicLogic.TouchAction.tap(2),0,"tap on air does not accidentally use an item");')
test_replace(touchtest,'eq(ClassicLogic.TouchAction.hold(0),-1,"hold on mob attacks");',
    'eq(ClassicLogic.TouchAction.hold(0),-2,"hold on mob interacts rather than auto-attacking");')

gradle=root/'app_pojavlauncher/build.gradle'
g=gradle.read_text()
a='versionCode 16400\n        versionName "1.6.4-camera-icons"'
assert g.count(a)==1
ng=g.replace(a,'versionCode 16500\n        versionName "1.6.5-camera-gesture-fix"',1)
gradle.write_text(ng)
print('Applied Jogger 1.6.5: native-camera authority, 3dp drag, classified tap/hold, stale-ray cleanup.')
