#!/usr/bin/env python3
"""Jogger 1.5.5: no camera steering on tap/hold; safe stationary hold mining.

Runs after keyboard-button-1.5.4/apply.py. Fails closed on source drift.
"""
from pathlib import Path
import sys

r=Path(sys.argv[1]).resolve()

def replace(path,old,new,count=1):
    p=r/path
    source=p.read_text()
    if source.count(old)!=count:
        raise RuntimeError(f"Jogger 1.5.5: {p}, anchor count {source.count(old)} != {count}")
    p.write_text(source.replace(old,new))

replace("app_pojavlauncher/build.gradle",
        '        versionCode 15400\n        versionName "1.5.4-mobile"',
        '        versionCode 15500\n        versionName "1.5.5-mobile"')

overlay="app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java"
file=r/overlay
before=file.read_text()

# Remove ALL legacy camera steering. No fake pointer aim, no hidden camera
# rotations, and no delayed move before the game sees the touch.
steer='boolean rotated=!raySupported&&aimLegacy(gesture.startX,gesture.startY);'
if before.count(steer)!=2:
    raise RuntimeError("Expected exactly two old tap/hold camera steering call sites")
before=before.replace(steer,
    'boolean rotated=false; // Touching MUST NOT rotate or teleport the camera.')

start=before.find('    /** Best-effort camera steering for Alpha/Beta/1.8')
end=before.find('    private void pulse(',start)
if start<0 or end<0 or before.count('    /** Best-effort camera steering for Alpha/Beta/1.8')!=1:
    raise RuntimeError("Legacy camera steering method anchor missing")
before=before[:start]+'''    // Legacy versions without off-centre ray support keep their own native
    // centre targeting. We never alter player rotation as a fallback.
'''+before[end:]

# A held/mine gesture must stay locked even if a finger shifts slightly after
# the long-press threshold. Drag-to-look still works BEFORE mining begins.
move='''                    gesture.move(x,y);
                    if(gesture.moved) {'''
if before.count(move)!=1:
    raise RuntimeError("Expected unique world pointer MOVE route")
before=before.replace(move,'''                    if(!gesture.mining)gesture.move(x,y);
                    if(gesture.moved&&!gesture.mining) {''')

# Unknown or disconnected game-thread hit reply: mine/attack, not use (the
# actual hit classification still selects use for a MISS or usable block).
old='finishHold(request,1)'
if before.count(old)!=2:
    raise RuntimeError("Expected two long-press fallback cases")
before=before.replace(old,'finishHold(request,0)')

# Old camera steering previously inserted an extra 85ms wait. No longer needed.
if before.count('rotated?85:0')!=2:
    raise RuntimeError("Expected exactly two legacy steer delays")
before=before.replace('rotated?85:0','0')

# Most importantly: if the user's finger is stationary, there is NO path
# from a tap/long-press to CallbackBridge mouse movement.
if 'aimLegacy(' in before:
    raise RuntimeError("Legacy camera steer is still reachable")
if before.count('CallbackBridge.mouseX+=')!=1 or before.count('CallbackBridge.sendCursorPos(')!=1:
    raise RuntimeError("Unexpected camera movement call site; audit touch router")
file.write_text(before)

# Older clients have no configurable FOV callback. Only enable provisional
# legacy FOV when structural bytecode scanning identified the full pick + view
# vector mapping. MobileTransformer can later replace it with measured FOV.
ray="jogger_touch_agent/src/main/java/com/minecraftjogger/touch/TouchRay.java"
replace(ray,
'''    public static void configure(Properties p){mappings=p;}''',
'''    public static void configure(Properties p){
        mappings=p;
        boolean mappedLegacy="true".equals(p.getProperty("adapter.legacy"))
            && p.getProperty("class.renderer")!=null
            && p.getProperty("class.entity")!=null
            && p.getProperty("class.vec")!=null
            && p.getProperty("method.pick")!=null
            && p.getProperty("method.view")!=null
            && p.getProperty("field.x")!=null
            && p.getProperty("field.y")!=null
            && p.getProperty("field.z")!=null;
        if(mappedLegacy&&!fovSeen){
            fov=70d; fovSeen=true;
            System.out.println("[Jogger] Legacy off-centre touch ray enabled using initial 70 degree FOV");
        }
    }''')
print("Jogger 1.5.5: removed all tap/hold camera steering; locked mining; guarded legacy touch ray.")
