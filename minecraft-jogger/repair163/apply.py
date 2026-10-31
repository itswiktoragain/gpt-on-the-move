#!/usr/bin/env python3
"""Jogger 1.6.3: preserve mining input through view drags; 1-pixel diagonal shafts.

Apply AFTER repair162/apply.py. Keep original signing identity out of the repo.
"""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
base = root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic'
overlay = base/'ClassicOverlay.java'
s = overlay.read_text()

def replace_exact(old,new):
    global s
    count=s.count(old)
    if count!=1:
        raise RuntimeError(f'Jogger 1.6.3 overlay source drift ({count} matches): {old[:120]!r}')
    s=s.replace(old,new,1)

# The actual 1.6.2 bug: the MOVE branch still calls inputs.set(-3), which
# emits native left-mouse UP. The previous fix only kept the ray active.
replace_exact(
    'handler.removeCallbacks(hold);pendingHold=-1;inputs.set(-3);if(MiningLookPolicy.clearTargetOnMove(gesture.mining))target(0,0,false);',
    '''handler.removeCallbacks(hold);
                        pendingHold=-1;  // invalidate any delayed classification reply
                        if (!MiningDragPolicy.keepAttackWhileLooking(gesture.mining,inputs.held(-1))) {
                            inputs.set(-3);  // before a long hold, ordinary look swipes cancel taps
                            target(0,0,false);
                        }
                        // On a real mining hold: preserve BOTH left-mouse DOWN
                        // and the original off-centre block ray until finger-up.''')

# Show the held target marker until finger-up, even when the finger has moved.
replace_exact('raySupported && gesture.active && !gesture.moved && targetEnabled()',
  'raySupported && gesture.active && (!gesture.moved || MiningDragPolicy.keepAttackWhileLooking(gesture.mining,inputs.held(-1))) && targetEnabled()')

# Preserve camera updates in both cases, and retain the 1.6.1 UP on release.
assert s.count('CallbackBridge.mouseX+=dx;')==1
assert s.count('inputs.set(-3);worldPointer=-1;')==1
assert 'JoggerTouchBridge.forceAttackRelease()' in s
assert 'if(!focused&&!stopped)release();' in s
assert 'MinecraftKeyboard.toggleManual()' in s
assert 'canvas.rotate(' not in s[s.index('} else if(button.icon!=null)'):s.index('@SuppressLint',s.index('} else if(button.icon!=null)'))]
overlay.write_text(s)
for name in ('MiningDragPolicy.java','PixelDirectionIcons.java'):
    shutil.copyfile(here/name,base/name)

build=root/'app_pojavlauncher/build.gradle'
s=build.read_text()
old='versionCode 16200\n        versionName "1.6.2-mining-look-icons"'
assert s.count(old)==1,'Expected 1.6.2 version anchor'
build.write_text(s.replace(old,'versionCode 16300\n        versionName "1.6.3-mining-continuity"',1))
print('Applied Jogger 1.6.3: left attack stays DOWN while looking; diagonal shafts exactly one pixel.')
