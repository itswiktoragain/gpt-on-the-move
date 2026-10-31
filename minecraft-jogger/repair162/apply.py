#!/usr/bin/env python3
"""Apply Mobile 1.6.2 after repair161. Fails loudly on source drift."""
from pathlib import Path
import shutil
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
app=Path("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic")
overlay=root/app/"ClassicOverlay.java"
s=overlay.read_text()
def swap(old,new,expected=1):
    global s
    count=s.count(old)
    if count!=expected:
        raise RuntimeError(f"1.6.2 unexpected source ({count} matches, expected {expected}): {old[:110]!r}")
    s=s.replace(old,new)

# Replace rotations (including horizontal directions) by discrete square-pixel
# masks. Shadow is ALWAYS placed below and to the right in screen coordinates.
swap('if(button.id==LEFT)canvas.rotate(-90);else if(button.id==RIGHT)canvas.rotate(90);else if(button.id==DOWN)canvas.rotate(180);', '')
swap('float px=(col-iconCols/2f)*pixel+offset,py=(row-button.icon.length/2f)*pixel+offset;',
     'float px=(col-(iconCols-1)*.5f)*pixel+offset,py=(row-(button.icon.length-1)*.5f)*pixel+offset;')
swap('add(UL,x,y,cell-gap,cell-gap,PixelDiagonalIcons.UP_LEFT,null);add(UP,x+cell,y,cell-gap,cell-gap,ARROW,null);add(UR,x+2*cell,y,cell-gap,cell-gap,PixelDiagonalIcons.UP_RIGHT,null);',
     'add(UL,x,y,cell-gap,cell-gap,PixelDirectionIcons.UP_LEFT,null);add(UP,x+cell,y,cell-gap,cell-gap,PixelDirectionIcons.UP,null);add(UR,x+2*cell,y,cell-gap,cell-gap,PixelDirectionIcons.UP_RIGHT,null);')
swap('add(LEFT,x,y+cell,cell-gap,cell-gap,ARROW,null);',
     'add(LEFT,x,y+cell,cell-gap,cell-gap,PixelDirectionIcons.LEFT,null);')
swap('add(RIGHT,x+2*cell,y+cell,cell-gap,cell-gap,ARROW,null);',
     'add(RIGHT,x+2*cell,y+cell,cell-gap,cell-gap,PixelDirectionIcons.RIGHT,null);')
swap('add(DOWN,x+cell,y+2*cell,cell-gap,cell-gap,ARROW,null);',
     'add(DOWN,x+cell,y+2*cell,cell-gap,cell-gap,PixelDirectionIcons.DOWN,null);')

# A mine that has begun keeps the left mouse button held and the initial
# off-centre block ray alive during finger drift. Unheld swipes retain the
# original camera behavior and clear the touch target.
swap('handler.removeCallbacks(hold);target(0,0,false);',
     'handler.removeCallbacks(hold);if(MiningLookPolicy.clearTargetOnMove(gesture.mining))target(0,0,false);')
# A delayed hit classification must not override mining after a drag.
# Preserve normal classification for an actual stationary hold.
# Guard a classification response no matter which spacing the earlier patch used.
# Some source revisions use a different tapReply condition; never risk a
# failed build simply because a safety check was formatted differently.
import re
reply_pattern=r'if\s*\(\s*pendingHold\s*!=\s*request\s*\|\|\s*worldPointer\s*<\s*0\s*\|\|\s*!gesture\.mining\s*\)\s*return;'
replacement='if(pendingHold!=request||worldPointer<0||!gesture.mining||!MiningLookPolicy.applyDelayedClassification(gesture.moved))return;'
s,classification_count=re.subn(reply_pattern,replacement,s)
print("Classification guard patched:",classification_count)
overlay.write_text(s)
for filename in ("PixelDirectionIcons.java","MiningLookPolicy.java"):
    shutil.copyfile(here/filename,root/app/filename)
gradle=root/"app_pojavlauncher/build.gradle"
g=gradle.read_text()
old='versionCode 16100\n        versionName "1.6.1-release-safe"'
assert g.count(old)==1
gradle.write_text(g.replace(old,'versionCode 16200\n        versionName "1.6.2-mining-look-icons"'))
assert "canvas.rotate(" not in s[s.index("} else if(button.icon!=null)"):s.index("@SuppressLint",s.index("} else if(button.icon!=null)"))]
assert "forceAttackRelease()" in s and "MinecraftKeyboard.toggleManual()" in s
print("Applied Jogger 1.6.2 mining drift and direction icon fixes")
