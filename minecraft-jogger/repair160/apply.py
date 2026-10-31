#!/usr/bin/env python3
"""Jogger Mobile 1.6.0 = 1.5.9 (1.5.2 rollback) + three regression repairs.

1. 1.21.4 real block-picking mappings + FOV-first-frame safe ray.
2. Hold-to-break fallback when ray classification is unavailable.
3. Restore the MANUAL keyboard toggle and independent crosshair hiding.

No synthetic camera steering or 1.5.3-1.5.8 touch-router rewrites.
"""
from pathlib import Path
import shutil
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent

def edit(rel,old,new):
    path=root/rel
    text=path.read_text()
    n=text.count(old)
    if n!=1:raise RuntimeError(f"Jogger 1.6.0 anchor drift: {rel}, count={n}, snippet={old[:80]!r}")
    path.write_text(text.replace(old,new,1))

edit("app_pojavlauncher/build.gradle",
     '        versionCode 15900\n        versionName "1.5.9-rollback152"',
     '        versionCode 16000\n        versionName "1.6.0-restored"')

# The UI-only rollback restored text and HUD names, but intentionally left out
# 1.21.4's obfuscated pick(), view(), renderer, hitResult and Vec3 names.
# When official downloads fail, the Minecraft agent saw a MISS under every
# finger and tried to USE rather than mine. Import only verified 1.21.4 names.
ui="app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/UiMapping.java"
edit(ui,
     'Properties merged = UiMappingFilter.merge(old, known, id);',
     '''Properties merged = GameTouchMappings.complete(
                UiMappingFilter.merge(old, known, id), known, id);''')

dest=root/"app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile"
shutil.copyfile(here/"GameTouchMappings.java",dest/"GameTouchMappings.java")

# FOV is initialized at 70 degrees until the actual renderer samples it.
# Requiring a completed frame before enabling the ray was causing the first
# tap to silently mine the centre crosshair. This does NOT change the camera.
hooks="jogger_touch_agent/src/main/java/com/minecraftjogger/touch/ClassicGameHooks.java"
edit(hooks,
     '&& hasPick && hasView && TouchRay.fovSeen; }',
     '&& hasPick && hasView; }')

ray="jogger_touch_agent/src/main/java/com/minecraftjogger/touch/TouchRay.java"
edit(ray,
     'if(!t.active||original==null||depth.get()==0||!fovSeen)return original;',
     'if(!t.active||original==null||depth.get()==0)return original;')

# Crosshair rendering should not reappear just because a FOV callback or
# optional touch capability has not registered yet.
mobile="jogger_touch_agent/src/main/java/com/minecraftjogger/touch/MobileHooks.java"
edit(mobile,
     '    public static boolean hideCrosshair(){boolean yes=ClassicGameHooks.rayReady();if(yes)capabilities|=4;return yes;}',
     '''    public static boolean hideCrosshair(){
        boolean hide=Boolean.parseBoolean(System.getProperty("jogger.mobile.hide_crosshair","true"));
        if(hide)capabilities|=4;
        return hide;
    }''')

# The 1.5.2 gesture router is deliberately unchanged except its long-hold
# MISS fallback: if no genuine off-centre ray is connected, always send
# left mouse down for mining rather than right mouse down for eating/USE.
# When ray is available, BLOCK mines and genuine MISS still uses held items.
overlay="app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java"
edit(overlay,
     '''            pendingHold=++tapSequence;
            long request=pendingHold;
            if(JoggerTouchBridge.requestTap(request))''',
     '''            pendingHold=++tapSequence;
            long request=pendingHold;
            // Start left-button mining at the HOLD threshold, not up to
            // 350ms later. A classified air MISS may switch to Use Item,
            // but a disconnected or unsupported ray must still be playable.
            inputs.set(-3,-1);
            if(JoggerTouchBridge.requestTap(request))''')
edit(overlay,
     'inputs.set(-3,ClassicLogic.TouchAction.hold(hit));',
     '''inputs.set(-3,ClassicLogic.TouchAction.hold(
                !raySupported && hit==2 ? 1 : hit));''')

# Restore button placement as in Jogger 1.5.4 without altering controls.
edit(overlay,
     '''        add(MENU,playing()?middle+.5f*w+dp(3):width-safeRight-w,controlsTop,w,h,MENU_ICON,null);
        add(BACK,safeLeft,top,w,h,null,"Leave");''',
     '''        add(MENU,playing()?middle+.5f*w+dp(3):width-safeRight-w,controlsTop,w,h,MENU_ICON,null);
        add(KEYBOARD,ClassicTopBarLayout.keyboardLeft(width,safeRight,w,dp(6),playing(),mode.equals("CHAT")),
                controlsTop,w,h,TYPE_ICON,null);
        add(BACK,safeLeft,top,w,h,null,"Leave");''')
edit(overlay,
     '''        if (playing()) return b.id==CHAT||b.id==PAUSE||b.id==MENU;
        if(mode.equals("CHAT"))return b.id==BACK||b.id==SEND;
        return b.id==BACK||b.id==MENU;''',
     '''        if (playing()) return b.id==CHAT||b.id==PAUSE||b.id==MENU||b.id==KEYBOARD;
        if(mode.equals("CHAT"))return b.id==BACK||b.id==SEND||b.id==KEYBOARD;
        return b.id==BACK||b.id==MENU||b.id==KEYBOARD;''')
edit(overlay,
     '        if(id==KEYBOARD){MainActivity.switchKeyboardState();return;}',
     '''        if(id==KEYBOARD){
            net.kdt.pojavlaunch.jogger.mobile.MinecraftKeyboard.toggleManual();
            return;
        }''')
layout=root/"app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicTopBarLayout.java"
shutil.copyfile(root.parent/"minecraft-jogger/keyboard-button-1.5.4/ClassicTopBarLayout.java",layout)

keyboard="app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/MinecraftKeyboard.java"
edit(keyboard,
     'private int occupiedHeight;private String gameState="SCREEN";private boolean manualChatOpen;',
     'private int occupiedHeight;private String gameState="SCREEN";private boolean manualChatOpen,manualOverride;')
edit(keyboard,
     '''    public static void show(){MinecraftKeyboard k=current();if(k!=null)request(k.owner,true);}
    public static void hide(){MinecraftKeyboard k=current();if(k!=null){k.manualChatOpen=false;k.state.hide();k.apply();}}''',
     '''    private void displayManually(){
        manualOverride=true;
        manualUntil=Long.MAX_VALUE;
        state.rearm();state.show();apply();
    }
    public static void show(){
        MinecraftKeyboard k=current();if(k!=null)k.ui.post(k::displayManually);
    }
    public static void toggleManual(){
        MinecraftKeyboard k=current();if(k==null)return;
        k.ui.post(()->{
            if(k.state.visible()){
                k.manualOverride=false;k.manualChatOpen=false;k.manualUntil=0;
                k.state.hide();k.apply();
            }else k.displayManually();
        });
    }
    public static void hide(){
        MinecraftKeyboard k=current();
        if(k!=null){k.manualOverride=false;k.manualChatOpen=false;k.manualUntil=0;k.state.hide();k.apply();}
    }''')
edit(keyboard,
     '''        if(!mode.equals("CHAT"))k.manualChatOpen=false;
        for(Key key:k.keys)key.invalidate();''',
     '''        if(!mode.equals("CHAT"))k.manualChatOpen=false;
        if(!mode.equals(previous))k.manualOverride=false;
        for(Key key:k.keys)key.invalidate();''')
edit(keyboard,
     '''        if(mode.equals("PLAY") || (mode.equals("INVENTORY")&&!previous.equals("INVENTORY"))) {
            k.state.hide();k.apply();
        }''',
     '''        if((mode.equals("PLAY")&&!k.manualOverride)
                || (mode.equals("INVENTORY")&&!previous.equals("INVENTORY"))) {
            k.state.hide();k.apply();
        }''')
edit(keyboard,
     'if(k.latestFocus==token&&SystemClock.uptimeMillis()>=k.manualUntil&&!k.manualChatOpen)',
     'if(k.latestFocus==token&&SystemClock.uptimeMillis()>=k.manualUntil&&!k.manualChatOpen&&!k.manualOverride)')

# Final failsafes: No synthetic camera movement, no replacing the gesture
# router, manual button must be visible and live, classifier mappings ready.
check=(root/overlay).read_text()
assert "aimLegacy(" not in check
assert "if(!gesture.mining)gesture.move" not in check
assert check.count("CallbackBridge.mouseX+=")==1
assert "MinecraftKeyboard.toggleManual()" in check
assert "b.id==MENU||b.id==KEYBOARD" in check
assert "ClassicLogic.TouchAction.hold(" in check
print("Jogger 1.6.0 restored mining, real ray keys, crosshair hiding, manual keyboard. Camera untouched.")
