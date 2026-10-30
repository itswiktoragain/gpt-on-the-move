#!/usr/bin/env python3
"""Apply verified 1.4 update atop Mobile 1.3 (after keyboard-icons/apply.py)."""
from pathlib import Path
import hashlib
import subprocess
import sys
root=Path(sys.argv[1]).resolve()
patch=Path(__file__).with_name("mobile14.patch").read_bytes()
expected="7d22a8d4bcd1fd66db865e84334ac1d9f5eb413632e82cfbbcbbed4f08bf1702"
if hashlib.sha256(patch).hexdigest()!=expected:
    raise SystemExit("Jogger 1.4 patch integrity check failed")
subprocess.run(["git","apply","--check","-"],input=patch,cwd=root,check=True)
subprocess.run(["git","apply","-"],input=patch,cwd=root,check=True)

# Resolve two 1.3-to-1.4 integration conflicts caught by the Android Java compiler.
main=root/"app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MainActivity.java"
java=main.read_text()
anchor="    @Override\n    private float keyboardTouchDownX"
if java.count(anchor)!=1:
    raise RuntimeError("Jogger MainActivity annotation anchor was not found")
java=java.replace(anchor,"    private float keyboardTouchDownX",1)
dispatch="    public boolean dispatchKeyEvent(KeyEvent event) {"
if java.count(dispatch)!=1:
    raise RuntimeError("Jogger MainActivity key dispatch anchor was not found")
java=java.replace(dispatch,"    @Override\n"+dispatch,1)
main.write_text(java)

gui=root/"app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/customcontrols/mouse/InGUIEventProcessor.java"
java=gui.read_text()
legacy="                if(motionEvent.getActionMasked()==MotionEvent.ACTION_UP && singleTap)\n                    Tools.MAIN_HANDLER.postDelayed(net.kdt.pojavlaunch.jogger.mobile.MinecraftKeyboard::gameTap,120);\n"
if java.count(legacy)!=1:
    raise RuntimeError("Legacy no-argument keyboard callback anchor was not found")
java=java.replace(legacy,"                // Text focus is requested using actual tap coordinates in MainActivity.\n",1)
gui.write_text(java)

print("Applied 1.4: no cut-off dots, proportional 8-pixel glyphs, tapped fields only, inventory charge/creative trash.")
