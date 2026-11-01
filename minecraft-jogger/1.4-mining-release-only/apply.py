#!/usr/bin/env python3
"""Exactly original Jogger 1.4.0+orientation fix, with mining RELEASE repair only.

NO mode routing, camera transforms, drag thresholds, targeting logic, button
layout, keyboard, or game-agent changes. Changes limited to:
- Recover the old mining press if Android loses its final UP/CANCEL pointer
- Release native left-click immediately rather than waiting for agent echo
- Reject stale mouse acknowledgements that could re-press/release mining
"""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
overlay=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java'
bridge=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/JoggerTouchBridge.java'
gradle=root/'app_pojavlauncher/build.gradle'
original=overlay.read_text()
old_bridge=bridge.read_text()
s=original
b=old_bridge

def edit_overlay(old,new):
    global s
    n=s.count(old)
    if n!=1:raise RuntimeError('Not the pinned 1.4 overlay; expected 1 anchor, got %d: %r'%(n,old[:170]))
    s=s.replace(old,new,1)

def edit_bridge(old,new):
    global b
    n=b.count(old)
    if n!=1:raise RuntimeError('Not the pinned 1.4 bridge; expected 1 anchor, got %d: %r'%(n,old[:170]))
    b=b.replace(old,new,1)

# Android can send CANCEL/OUTSIDE with no valid pointer index, and a fresh
# ACTION_DOWN can happen after an earlier final ACTION_UP was lost.
# This runs BEFORE the original ACTION_DOWN gameplay routing.
edit_overlay(
'''        layoutControls();int action=event.getActionMasked(),index=event.getActionIndex(),id=event.getPointerId(index);''',
'''        layoutControls();int action=event.getActionMasked(),index=event.getActionIndex();
        if(action==MotionEvent.ACTION_OUTSIDE){release();return true;}
        int id=index>=0&&index<event.getPointerCount()?event.getPointerId(index):-1;
        // A fresh DOWN starts a new Android gesture. A previous touch still
        // recorded here means its UP was lost; release its mining button.
        if(action==MotionEvent.ACTION_DOWN && (worldPointer>=0||routes.size()>0))release();''')

# Fix the original 1.4.0 release-on-drag bug: the game still owns the left
# button once a long-hold begins, even if this finger later looks around.
# Only guard the input release; leave the cursor's MOVE/delta math untouched.
edit_overlay(
    'handler.removeCallbacks(hold);inputs.set(-3);target(0,0,false);',
    'handler.removeCallbacks(hold);if(!gesture.mining)inputs.set(-3);target(0,0,false);')

# The first/final finger lifting must clear mining even if its Route was lost.
edit_overlay(
'''        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP) {
            Route route=routes.get(id);routes.remove(id);inputs.set(id+100);
            if(route!=null) {''',
'''        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP) {
            // If Android's final UP no longer matches the old owner, do not
            // leave a hidden mining press held. Other fingers cannot exist
            // on ACTION_UP (as opposed to ACTION_POINTER_UP).
            if(action==MotionEvent.ACTION_UP && worldPointer>=0 && id!=worldPointer){
                release();return true;
            }
            Route route=routes.get(id);routes.remove(id);inputs.set(id+100);
            if(route==null && (id==worldPointer ||
                    (action==MotionEvent.ACTION_UP && inputs.held(-1)))){
                release();return true;
            }
            // The dedicated Mine button has a different input owner (-1),
            // so only force an UP after the very last owner releases.
            if(route!=null && route.kind==MINE && !inputs.held(-1))
                JoggerTouchBridge.releaseAttackIfUnheld();
            if(route!=null) {''')
edit_overlay(
'''                    inputs.set(-3);worldPointer=-1;long generation=gestureGeneration;''',
'''                    inputs.set(-3);worldPointer=-1;long generation=gestureGeneration;
                    if(!inputs.held(-1))JoggerTouchBridge.releaseAttackIfUnheld();''')
edit_overlay(
'''        inputs.clear();routes.clear();sneaking=sprinting=false;sprint.reset();target(0,0,false);invalidate();''',
'''        inputs.clear();
        JoggerTouchBridge.releaseAttackIfUnheld();
        routes.clear();sneaking=sprinting=false;sprint.reset();target(0,0,false);invalidate();''')

# The mouse is transmitted over an asynchronous local socket. A stale DOWN
# echo can arrive on the Android main thread after the finger is UP; a stale
# UP can arrive after a NEW hold started. Both violate native desired state.
# This gate modifies ONLY native left-mouse acknowledgement delivery.
edit_bridge(
'''if (connection == current && epoch == incomingEpoch) deliverMouse(button, mods, state == 1);''',
'''if(connection==current && epoch==incomingEpoch) {
                            if(button==0) {
                                boolean wanted=HELD_MOUSE.containsKey(0);
                                if(state==1 && !wanted) {
                                    // Preserve a short tap's click edge, but
                                    // do not leave the old DOWN latched.
                                    deliverMouse(0,mods,true);
                                    deliverMouse(0,0,false);
                                }else if(state==0 && wanted) {
                                    // The UP belongs to a previous hold; a
                                    // fresh finger already owns left click.
                                }else deliverMouse(button,mods,state==1);
                            }else deliverMouse(button,mods,state==1);
                        }''')

# The physical UP must be emitted NOW, never delayed until a socket roundtrip.
# HELD_MOUSE is the desired owner state established by ClassicLogic.Inputs;
# don't interrupt another finger that still owns the mining key.
edit_bridge(
'''    private static void deliverKey(int key, char character, int scan, int mods, boolean down) {''',
'''    /**
     * End touch mining at the native boundary immediately after the last
     * owner is released. Never touches cursor, camera, grab or key modifiers.
     */
    public static void releaseAttackIfUnheld() {
        synchronized(LOCK) {
            if(HELD_MOUSE.containsKey(0))return;
            deliverMouse(0,0,false);
        }
    }

    private static void deliverKey(int key, char character, int scan, int mods, boolean down) {''')

# Verify original camera and native-grab path are literally unchanged.
def block(data,begin,end):
    i=data.index(begin)
    return data[i:data.index(end,i)]
for start,end in [
 ('    public static void report(String state, boolean capability) {','    public static void physicalKey('),
 ('    @Override public void onGrabState(boolean grabbed) {','    @Override public void onOptionChanged()'),
 ('    private boolean playing() {','    private float dp('),
 ('    private boolean targetEnabled()','    public void release()')
]:
    assert block(s,start,end)==block(original,start,end),'UNSAFE: touched original camera/routing block '+start
# The ONLY change inside ACTION_MOVE is retaining the ongoing attack hold.
old_move=block(original,'        if(action==MotionEvent.ACTION_MOVE) {','        if(action==MotionEvent.ACTION_CANCEL)')
new_move=block(s,'        if(action==MotionEvent.ACTION_MOVE) {','        if(action==MotionEvent.ACTION_CANCEL)')
old_cancel='handler.removeCallbacks(hold);inputs.set(-3);target(0,0,false);'
assert old_move.count(old_cancel)==1
assert new_move==old_move.replace(old_cancel,
    'handler.removeCallbacks(hold);if(!gesture.mining)inputs.set(-3);target(0,0,false);',1), 'UNSAFE: camera movement or gesture dispatch changed'
assert s.count('CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY)')==1,'Original native camera path changed'
assert original.count('CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY)')==1
assert b.count('CallbackBridge.sendCursorPos(')==old_bridge.count('CallbackBridge.sendCursorPos('),'Bridge changed camera'
assert b.count('CallbackBridge.isGrabbing()')==old_bridge.count('CallbackBridge.isGrabbing()'),'Bridge changed grab logic'
assert s.count('if(action==MotionEvent.ACTION_CANCEL){release();return true;}')==1,'CANCEL release missing'
assert 'MinecraftKeyboard.toggleManual()' not in s,'Post-1.4 keyboard patch accidentally applied'
overlay.write_text(s)
bridge.write_text(b)

v=gradle.read_text()
old='versionCode 17000\n        versionName "1.4.0-orientation-only"'
assert v.count(old)==1,'Expected original 1.4.0-orientation-only version'
gradle.write_text(v.replace(old,'versionCode 17100\n        versionName "1.4.0-orientation-mining-release"',1))
print('1.4.0 mining release fixed. Camera, touch MOVE, grab, GUI mode, timing and agent all unchanged.')
