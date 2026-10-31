#!/usr/bin/env python3
"""Jogger Mobile 1.6.1: release-safe mining and true raster diagonal controls.

Runs AFTER repair160/apply.py. This is a narrowly scoped bug fix:
- Native mouse-up is immediate and robust to late game-agent echo packets.
- Orphaned/lost touch pointers, cancel, app focus loss all stop mining.
- Diagonal movement arrows are rasterized from axis-aligned 9x9 cells.
No change to camera movement, off-centre ray math, HUD, or inventory.
"""
from pathlib import Path
import shutil
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent

def edit(path, old, new):
    file=root/path
    content=file.read_text()
    n=content.count(old)
    if n!=1:
        raise RuntimeError(f"1.6.1 source drift in {path}: expected unique anchor, found {n}: {old[:100]!r}")
    file.write_text(content.replace(old,new,1))

edit("app_pojavlauncher/build.gradle",
     '        versionCode 16000\n        versionName "1.6.0-restored"',
     '        versionCode 16100\n        versionName "1.6.1-release-safe"')

overlay="app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java"
bridge="app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/JoggerTouchBridge.java"

# Actual pixel art, no 45-degree rotated canvases. Cardinal arrow rotations
# stay at 0/90/180 degrees, and each diagonal has its own square-cell bitmap.
edit(overlay,
     'add(UL,x,y,cell-gap,cell-gap,ARROW,null);add(UP,x+cell,y,cell-gap,cell-gap,ARROW,null);add(UR,x+2*cell,y,cell-gap,cell-gap,ARROW,null);',
     'add(UL,x,y,cell-gap,cell-gap,PixelDiagonalIcons.UP_LEFT,null);add(UP,x+cell,y,cell-gap,cell-gap,ARROW,null);add(UR,x+2*cell,y,cell-gap,cell-gap,PixelDiagonalIcons.UP_RIGHT,null);')
edit(overlay,
     'if(button.id==LEFT)canvas.rotate(-90);else if(button.id==RIGHT)canvas.rotate(90);else if(button.id==DOWN)canvas.rotate(180);else if(button.id==UL)canvas.rotate(-45);else if(button.id==UR)canvas.rotate(45);',
     'if(button.id==LEFT)canvas.rotate(-90);else if(button.id==RIGHT)canvas.rotate(90);else if(button.id==DOWN)canvas.rotate(180);')
edit(overlay,
     'paint.setColor(pass==0?0xff202020:0xfff4f4f4);float offset=pass==0?pixel*.5f:0;',
     'paint.setColor(pass==0?0xff202020:0xfff4f4f4);float offset=pass==0?pixel:0;')

# The old release could end at a different pointer ID or lose ACTION_UP.
# A new DOWN has exactly one active finger: discard any orphaned mining state.
edit(overlay,
     '        layoutControls();int action=event.getActionMasked(),index=event.getActionIndex(),id=event.getPointerId(index);',
     '''        layoutControls();int action=event.getActionMasked(),index=event.getActionIndex();
        // CANCEL and OUTSIDE can arrive without an active pointer.
        int id=index>=0&&index<event.getPointerCount()?event.getPointerId(index):-1;
        if(action==MotionEvent.ACTION_OUTSIDE){release();return true;}
        if(action==MotionEvent.ACTION_DOWN && worldPointer>=0)release();''')

# On MOVE, if Android no longer reports the tracked attack pointer, clear it.
# This does not affect holding still: the pointer remains present in MOVE.
edit(overlay,
     '''        if(action==MotionEvent.ACTION_MOVE) {
            for(int i=0;i<event.getPointerCount();i++) {''',
     '''        if(action==MotionEvent.ACTION_MOVE) {
            if(worldPointer>=0){
                boolean worldFingerPresent=false;
                for(int i=0;i<event.getPointerCount();i++)
                    if(event.getPointerId(i)==worldPointer){worldFingerPresent=true;break;}
                if(!worldFingerPresent){release();return true;}
            }
            for(int i=0;i<event.getPointerCount();i++) {''')

edit(overlay,
     '''        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP) {
            Route route=routes.get(id);routes.remove(id);inputs.set(id+100);''',
     '''        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP) {
            // ACTION_UP means the final finger has gone, regardless of
            // a missing or mismatched world route. Never leave mining latched.
            if(action==MotionEvent.ACTION_UP&&worldPointer>=0&&id!=worldPointer){
                release();return true;
            }
            Route route=routes.get(id);routes.remove(id);inputs.set(id+100);''')
edit(overlay,
     'Route route=routes.get(id);routes.remove(id);inputs.set(id+100);',
     '''Route route=routes.get(id);routes.remove(id);inputs.set(id+100);
            if(route==null&&id==worldPointer){release();return true;}''')
edit(overlay,
     'pendingHold=-1;inputs.set(-3);worldPointer=-1;long generation=gestureGeneration;',
     '''pendingHold=-1;inputs.set(-3);worldPointer=-1;long generation=gestureGeneration;
                    if(!inputs.held(-1))JoggerTouchBridge.forceAttackRelease();''')

# Foreground loss and view teardown are other ways to miss ACTION_UP.
edit(overlay,
     '''    @Override public void onGrabState(boolean grabbed) {
        handler.post(() -> {''',
     '''    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(!focused&&!stopped)release();
    }
    @Override public void onGrabState(boolean grabbed) {
        handler.post(() -> {''')
edit(overlay,
     'inputs.clear();routes.clear();sneaking=sprinting=false;sprint.reset();target(0,0,false);invalidate();',
     'inputs.clear();JoggerTouchBridge.forceAttackRelease();routes.clear();sneaking=sprinting=false;sprint.reset();target(0,0,false);invalidate();')

# Close the race at the actual native mouse-delivery boundary:
# if the remote JVM echoes a delayed DOWN after UP, NEVER dispatch that stale
# press. The newest intended state in HELD_MOUSE always wins.
edit(bridge,
     'if (connection == current && epoch == incomingEpoch) deliverMouse(button, mods, state == 1);',
     '''if (connection == current && epoch == incomingEpoch) {
                            boolean wanted=HELD_MOUSE.containsKey(button);
                            if(MouseAckPolicy.shouldForward(button,state==1,wanted))
                                deliverMouse(button,mods,state==1);
                            else if(button==0&&!wanted)deliverMouse(0,0,false);
                        }''')

# In addition to the socket release, emit a native UP synchronously.
# A missing DOWN or duplicate UP is harmless; a missing UP is not.
edit(bridge,
     '    private static String lastTouchTarget = "";',
     '''    /** Stop attacking immediately, even if a delayed game-agent reply
     * arrives later. Does not release WASD, jump, or the right mouse button. */
    public static void forceAttackRelease() {
        synchronized (LOCK) {
            HELD_MOUSE.remove(0);
            Connection current=connection;
            if(current!=null&&!current.queue.offer("M "+epoch+" 0 0 0"))
                disconnect(current);
            deliverMouse(0,0,false);
        }
    }

    private static String lastTouchTarget = "";''')

api=root/"app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger"
api.mkdir(parents=True,exist_ok=True)
shutil.copyfile(here/"MouseAckPolicy.java",api/"MouseAckPolicy.java")
icons=root/"app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic"
shutil.copyfile(here/"PixelDiagonalIcons.java",icons/"PixelDiagonalIcons.java")

# Audit avoids accidental reintroduction of the camera warp workaround.
source=(root/overlay).read_text()
assert "aimLegacy(" not in source
assert "canvas.rotate(-45)" not in source and "canvas.rotate(45)" not in source
assert source.count("CallbackBridge.mouseX+=")==1
assert "MinecraftKeyboard.toggleManual()" in source
assert "if(!inputs.held(-1))JoggerTouchBridge.forceAttackRelease();" in source
assert "if(!focused&&!stopped)release();" in source
assert "getPointerId(i)==worldPointer" in source
print("Jogger 1.6.1: release-safe mining + true square-pixel diagonal arrow icons applied.")
