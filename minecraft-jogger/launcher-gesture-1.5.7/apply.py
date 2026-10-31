#!/usr/bin/env python3
"""Restore Jogger 1.3-era touch/drag behavior, not older Minecraft mappings.

Runs AFTER touch-ray-1.5.6/apply.py. All anchors must match once.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()

def edit(rel, old, new):
    path=root/rel
    source=path.read_text()
    count=source.count(old)
    if count!=1:
        raise RuntimeError("Jogger 1.5.7 source drift in %s: anchor count %d" % (rel,count))
    path.write_text(source.replace(old,new,1))

edit("app_pojavlauncher/build.gradle",
     '        versionCode 15600\n        versionName "1.5.6-mobile"',
     '        versionCode 15700\n        versionName "1.5.7-mobile"')

overlay="app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java"

# Jogger 1.5.5 mistakenly stopped handling MOVE events as soon as mining
# started. Jogger 1.3 handled user movement on every world-pointer MOVE,
# even after a long press, and cancelled mining before moving the camera.
edit(overlay,
     '''                    if(!gesture.mining)gesture.move(x,y);
                    if(gesture.moved&&!gesture.mining) {''',
     '''                    // Earlier Jogger releases always handled actual finger
                    // movement, including after mining began. Stationary
                    // holding must NEVER rotate or warp the camera.
                    gesture.move(x,y);
                    if(gesture.moved) {''')

# Under the original touch slop, update the off-centre ray as the finger
# slides. Above slop, switch to the original smooth drag-to-look path.
# This is actual pointer movement, NOT synthetic camera steering.
edit(overlay,
     '''                        CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY);
                    }
                    gesture.lastX=x;gesture.lastY=y;''',
     '''                        CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY);
                    } else if(gesture.mining) {
                        // Small aim correction while mining follows the finger.
                        // The user can still drag farther to look as in 1.3.
                        target(x,y,true);
                    }
                    gesture.lastX=x;gesture.lastY=y;''')

# Make transition safely cancel a pending game-thread hold classification.
# The earlier v1.5.0 gesture change already does this via pendingHold=-1.
source=(root/overlay).read_text()
expected=[
  "handler.removeCallbacks(hold);pendingHold=-1;inputs.set(-3);target(0,0,false);",
  "CallbackBridge.mouseX+=dx;CallbackBridge.mouseY+=dy;",
  "if(gesture.moved) {",
  "else if(gesture.mining) {",
  "gesture.move(x,y);",
  "gesture.begin(x,y,event.getEventTime(),dp(8));target(x,y,true);",
  "inputs.set(-3);worldPointer=-1;",
]
for required in expected:
    if required not in source:
        raise RuntimeError("Jogger 1.5.7 lost required original gesture semantics: "+required)
if "aimLegacy(" in source:
    raise RuntimeError("Camera-jump workaround unexpectedly reintroduced")
if source.count("CallbackBridge.mouseX+=")!=1:
    raise RuntimeError("Camera must move only in the drag-to-look code path")
print("Jogger 1.5.7: restored 1.3-era move/hold/drag behavior; no camera warp.")
