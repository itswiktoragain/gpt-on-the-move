#!/usr/bin/env python3
"""Jogger 1.5.8: connect the real version-aware Minecraft mapping loader to
the shipped game process. Without this, obfuscated clients always pick the
crosshair even though off-centre ray bytecode patches exist.

Also permit default FOV 70 before the FOV observer reports the first frame.
Run AFTER launcher-gesture-1.5.7/apply.py.
"""
from pathlib import Path
import sys

root=Path(sys.argv[1]).resolve()
def fix(path,old,new):
    p=root/path
    s=p.read_text()
    n=s.count(old)
    if n!=1: raise RuntimeError("Jogger 1.5.8 drift: %s expected 1 anchor, found %s" % (path,n))
    p.write_text(s.replace(old,new,1))

fix("app_pojavlauncher/build.gradle",
    '        versionCode 15700\n        versionName "1.5.7-mobile"',
    '        versionCode 15800\n        versionName "1.5.8-mobile"')

bridge="app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/JoggerTouchBridge.java"
fix(bridge,
    'File mapping = net.kdt.pojavlaunch.jogger.classic.GameAssets.mappings(context);',
    '''// The previous launcher called GameAssets.mappings(), bypassing
        // VersionAssets and its verified 1.21.4 offline fallback entirely.
        // On obfuscated Minecraft, a missing map makes every action use the
        // centre crosshair despite sending the correct finger coordinates.
        File mapping = net.kdt.pojavlaunch.jogger.mobile.VersionAssets.mappings(context);
        if (mapping == null) {
            Log.w(TAG, "No version-aware Minecraft ray mapping. Off-centre targeting is unavailable for this version.");
        } else {
            Log.i(TAG, "Loading Minecraft touch mappings for off-centre finger targeting");
        }''')

agent="jogger_touch_agent/src/main/java/com/minecraftjogger/touch/ClassicGameHooks.java"
fix(agent,
    '''public static boolean rayReady() { return Boolean.parseBoolean(System.getProperty("jogger.mobile.target","true")) && hasPick && hasView && TouchRay.fovSeen; }''',
    '''public static boolean rayReady() {
        // 70-degree fallback is available from launch; FOV may not have
        // been sampled at the first tap. Only require actual transformed
        // pick and look-vector methods, never claim support without hooks.
        return Boolean.parseBoolean(System.getProperty("jogger.mobile.target","true"))
            && hasPick && hasView;
    }''')
ray="jogger_touch_agent/src/main/java/com/minecraftjogger/touch/TouchRay.java"
fix(ray,
    'if(!t.active||original==null||depth.get()==0||!fovSeen)return original;',
    '''// An observed FOV improves accuracy, but the default 70 degrees is
        // enough to target a finger from the first frame. Do not silently
        // revert to the crosshair while waiting for the FOV callback.
        if(!t.active||original==null||depth.get()==0)return original;''')
bridge_text=(root/bridge).read_text()
if 'File mapping = net.kdt.pojavlaunch.jogger.classic.GameAssets.mappings(context);' in bridge_text:
    raise RuntimeError("Legacy mapping loader still wired to launch")
if 'net.kdt.pojavlaunch.jogger.mobile.VersionAssets.mappings(context)' not in bridge_text:
    raise RuntimeError("Updated mapping loader not connected")
print("Jogger 1.5.8: actual live-game version mapping wired; first tap uses finger ray.")
