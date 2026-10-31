#!/usr/bin/env python3
"""Jogger 1.6.6: keep Android touch streams alive through transient mouse-grab drops.

Apply after repair165. Camera movement is still exclusively delivered through
the launcher's native CallbackBridge, never by modifying Minecraft via agent.
"""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
base=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic'
overlay=base/'ClassicOverlay.java'
s=overlay.read_text()
def edit(old,new):
    global s
    n=s.count(old)
    if n!=1:raise RuntimeError(f'1.6.6 overlay drift ({n} matches): {old[:110]!r}')
    s=s.replace(old,new,1)

edit('private String mode = "SCREEN";',
     'private String mode = "SCREEN", lastGameMode = "SCREEN";')
edit('''            if (overlay.stopped) return;
            String effective=TouchRoutingPolicy.effectiveMode(CallbackBridge.isGrabbing(),state,overlay.mode);''',
     '''            if (overlay.stopped) return;
            overlay.lastGameMode=state;
            String effective=TouchRoutingPolicy.effectiveMode(CallbackBridge.isGrabbing(),state,overlay.mode);''')
edit('''            if(!TouchRoutingPolicy.isNewGrabState(grabbed,mode))return;
            release();''',
     '''            if(!TouchRoutingPolicy.isNewGrabState(grabbed,mode))return;
            // Wait for an explicit real GUI report before dropping the active
            // gesture. A transient native ungrab during a block place is not
            // proof that Minecraft has opened a GUI.
            if(TouchRoutingPolicy.preserveWorldOnUngrab(grabbed,mode,lastGameMode)){
                android.util.Log.w("JoggerCamera","Retaining world touch through transient native ungrab");
                return;
            }
            release();''')
edit('private boolean playing() { return !stopped && CallbackBridge.isGrabbing(); }',
     'private boolean playing() { return !stopped && TouchRoutingPolicy.canRouteWorld(CallbackBridge.isGrabbing(),mode); }')
# Returning false from ACTION_DOWN loses ALL subsequent Android ACTION_MOVE
# events for that gesture. Never reject a gameplay DOWN due to a brief false
# grabbed flag, but still let genuine GUI clicks go to the underlying view.
edit('''        if(action==MotionEvent.ACTION_DOWN && !playing() && hit(event.getX(),event.getY())==null)return false;''',
     '''        // Only forward a true GUI DOWN. Recovered PLAY must capture DOWN
        // or Android will not deliver any later MOVE in this touch stream.
        if(action==MotionEvent.ACTION_DOWN && !playing() && hit(event.getX(),event.getY())==null)return false;''')

assert 'TouchRoutingPolicy.preserveWorldOnUngrab(grabbed,mode,lastGameMode)' in s
assert 'TouchRoutingPolicy.canRouteWorld(CallbackBridge.isGrabbing(),mode)' in s
assert s.count('CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY)')==2
assert 'JoggerTouchBridge.forceAttackRelease()' in s
assert 'MinecraftKeyboard.toggleManual()' in s
overlay.write_text(s)

policy=base/'TouchRoutingPolicy.java'
t=policy.read_text()
old='''        if("PLAY".equals(reported)) return "PLAY".equals(current) ? "SCREEN" : current;'''
new='''        // A confirmed world report can repair a stale false native-grab flag
        // without overriding an intentional chat or inventory screen.
        if("PLAY".equals(reported))
            return "CHAT".equals(current)||"INVENTORY".equals(current) ? current : "PLAY";'''
assert t.count(old)==1,'Missing 1.6.5 mode policy'
t=t.replace(old,new,1)
needle='''    /** Never accept a grab-change callback superseded by the latest native state. */'''
extra='''    /** World touches remain owned by the launcher while the game is in PLAY. */
    public static boolean canRouteWorld(boolean nativeGrabbed,String mode) {
        return nativeGrabbed||"PLAY".equals(mode);
    }
    /** A false grabbed flag alone is not evidence of an actual GUI. */
    public static boolean preserveWorldOnUngrab(boolean grabbed,String mode,String lastGameMode) {
        return !grabbed&&"PLAY".equals(mode)&&"PLAY".equals(lastGameMode);
    }
'''
assert t.count(needle)==1
policy.write_text(t.replace(needle,extra+needle,1))

gradle=root/'app_pojavlauncher/build.gradle'
g=gradle.read_text()
old='versionCode 16500\n        versionName "1.6.5-camera-gesture-fix"'
assert g.count(old)==1
gradle.write_text(g.replace(old,'versionCode 16600\n        versionName "1.6.6-camera-stream-recovery"',1))
print('Applied Jogger 1.6.6: native-grab glitch recovery and uninterrupted world gesture streams')
