#!/usr/bin/env python3
"""Minecraft Jogger 1.6.7: restore actions; separate 2.5dp camera from 17dp tap/hold slop."""
from pathlib import Path
import sys,shutil
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
java=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic'
p=java/'ClassicOverlay.java';s=p.read_text()
def edit(old,new):
    global s
    matches=s.count(old)
    if matches!=1:raise RuntimeError(f'1.6.7 source drift: {matches} matches for {old[:140]!r}')
    s=s.replace(old,new,1)

# 1.6.5 made camera and action-gesture slop the same tiny 3dp threshold,
# so natural thumb movement invalidated every tap, place, and long-hold.
edit('gesture.begin(x,y,event.getEventTime(),dp(TouchRoutingPolicy.cameraSlopDp()));',
     'gesture.begin(x,y,event.getEventTime(),dp(WorldGesturePolicy.actionSlopDp()));')
edit('if(CameraLookPolicy.shouldRotate(gesture.moved,gesture.mining,inputs.held(-1))) {',
     '''if(WorldGesturePolicy.rotate(cameraTurning,gesture.mining,
                            x-gesture.startX,y-gesture.startY,dp(WorldGesturePolicy.cameraSlopDp()))) {''')
# Camera moves with 2.5dp threshold, but gesture.moved is only set past 17dp.
# Do not kill a pending mining hold or clear the off-centre ray just because
# someone moved the view 3dp while keeping their finger still enough to mine.
edit('''                        handler.removeCallbacks(hold);
                        if(!gesture.mining){
                            pendingHold=-1;pendingProbe=-1;
                            inputs.set(-3);  // ordinary camera swipe cancels an unfinished tap
                            target(0,0,false);
                        }''',
     '''                        if(gesture.moved && !gesture.mining){
                            handler.removeCallbacks(hold);
                            pendingHold=-1;pendingProbe=-1;
                            inputs.set(-3); // real swipe, not a tiny accidental wiggle
                            target(0,0,false);
                        }''')

# If the agent cannot provide off-centre block results, do NOT interpret all
# taps as AIR and do nothing; fall back to vanilla centre-crosshair actions.
# We still use genuine ENTITY/BLOCK/MISS whenever the target is available.
edit('''        inputs.set(-3,ClassicLogic.TouchAction.hold(hit));''',
     '''        inputs.set(-3,ClassicLogic.TouchAction.hold(
                WorldGesturePolicy.correctHit(hit,raySupported,targetEnabled())));''')
edit('''        int code=ClassicLogic.TouchAction.tap(button);''',
     '''        int code=ClassicLogic.TouchAction.tap(
                WorldGesturePolicy.correctHit(button,raySupported,targetEnabled()));''')

# Keep the original 1.6.6 anti-lockup path. Camera movement must always stay
# on the launcher side, and every input must be released on finger-up.
assert 'TouchRoutingPolicy.canRouteWorld(CallbackBridge.isGrabbing(),mode)' in s
assert 'TouchRoutingPolicy.preserveWorldOnUngrab(grabbed,mode,lastGameMode)' in s
assert s.count('CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY)')==2
assert 'JoggerTouchBridge.forceAttackRelease()' in s
assert 'worldPointer=-1;lookPointer=-1;cameraTurning=false;lookMoved=false;' in s
assert 'MinecraftKeyboard.toggleManual()' in s
p.write_text(s)
shutil.copyfile(here/'WorldGesturePolicy.java',java/'WorldGesturePolicy.java')

# A delayed or disconnected classifier must not make ALL mining attempts
# turn into right-click use-item events just because the agent once announced
# ray support. Prioritize holding left click for missing classifications.
policy=java/'TouchRoutingPolicy.java';t=policy.read_text()
a='public static int holdFallbackHit(boolean rayKnown) { return rayKnown ? 2 : 1; }'
assert t.count(a)==1
policy.write_text(t.replace(a,'public static int holdFallbackHit(boolean rayKnown) { return 1; }',1))

gradle=root/'app_pojavlauncher/build.gradle';g=gradle.read_text()
a='versionCode 16600\n        versionName "1.6.6-camera-stream-recovery"'
assert g.count(a)==1
gradle.write_text(g.replace(a,'versionCode 16700\n        versionName "1.6.7-interaction-recovery"',1))
print('Jogger 1.6.7: 2.5dp camera, 17dp gameplay intent, native input and safe fallback restored')
