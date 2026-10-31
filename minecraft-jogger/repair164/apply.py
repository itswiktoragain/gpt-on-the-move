#!/usr/bin/env python3
"""Minecraft Jogger 1.6.4 on top of repair163: multi-finger look and centered sprites."""
from pathlib import Path
import shutil
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
java=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic'
overlay=java/'ClassicOverlay.java'
s=overlay.read_text()
def edit(old,new):
    global s
    n=s.count(old)
    if n!=1:raise RuntimeError(f'1.6.4 source drift: {n} instances of {old[:100]!r}')
    s=s.replace(old,new,1)

# An additional finger on world space is a camera-look pointer, not IGNORED.
# It is independent from the primary finger that is holding mining.
edit('private static final int WORLD=100, HOTBAR=101, IGNORED=102;',
     'private static final int WORLD=100, HOTBAR=101, IGNORED=102, LOOK=103;')
edit('private int worldPointer = -1, pulseOwner = -10000;',
     'private int worldPointer = -1, lookPointer = -1, pulseOwner = -10000;\n    private float lookX, lookY;')
edit('if(action==MotionEvent.ACTION_DOWN && worldPointer>=0)release();',
     'if(action==MotionEvent.ACTION_DOWN && (worldPointer>=0||lookPointer>=0))release();')
edit('''            }else routes.put(id,new Route(IGNORED,null));
            invalidate();return true;''',
     '''            }else if(CameraLookPolicy.routeSecondLook(playing(),worldPointer>=0,lookPointer>=0)) {
                routes.put(id,new Route(LOOK,null));lookPointer=id;lookX=x;lookY=y;
            }else routes.put(id,new Route(IGNORED,null));
            if((worldPointer==id||lookPointer==id)&&getParent()!=null)
                getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();return true;''')
edit('''            for(int i=0;i<event.getPointerCount();i++) {
                int pointer=event.getPointerId(i);Route route=routes.get(pointer);if(route==null)continue;''',
     '''            if(lookPointer>=0){
                boolean lookFingerPresent=false;
                for(int i=0;i<event.getPointerCount();i++)
                    if(event.getPointerId(i)==lookPointer){lookFingerPresent=true;break;}
                if(!lookFingerPresent){routes.remove(lookPointer);lookPointer=-1;}
            }
            for(int i=0;i<event.getPointerCount();i++) {
                int pointer=event.getPointerId(i);Route route=routes.get(pointer);if(route==null)continue;''')
# The 1.6.3 attack-preservation fix remains, but a tiny movement AFTER the
# long-press threshold must rotate the camera without waiting for swipe slop.
edit('if(gesture.moved) {',
     'if(CameraLookPolicy.shouldRotate(gesture.moved,gesture.mining,inputs.held(-1))) {')
edit('''                }else if(directional(route.button)) {
                    Button next=hit(x,y);''',
     '''                }else if(route.kind==LOOK&&pointer==lookPointer&&playing()) {
                    float factor=1800f/Math.max(1,getHeight())*LauncherPreferences.PREF_MOUSESPEED;
                    float dx=(x-lookX)*factor,dy=(y-lookY)*factor;
                    if(dx!=0||dy!=0){
                        CallbackBridge.mouseX+=dx;CallbackBridge.mouseY+=dy;
                        CallbackBridge.deltaX=dx;CallbackBridge.deltaY=dy;
                        CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY);
                    }
                    lookX=x;lookY=y;
                }else if(directional(route.button)) {
                    Button next=hit(x,y);''')
edit('''                if(route.kind==HOTBAR)lastSlot=-1;''',
     '''                if(route.kind==HOTBAR)lastSlot=-1;
                if(route.kind==LOOK&&id==lookPointer)lookPointer=-1;''')
edit('gestureGeneration++;gesture.cancel();worldPointer=-1;lastSlot=-1;',
     'gestureGeneration++;gesture.cancel();worldPointer=-1;lookPointer=-1;lastSlot=-1;')

# Original 1.6.2 centered the bitmap's FULL declared size, not its actual
# nonempty ink, and then drew the shadow one whole pixel to bottom-right.
# As a result every glyph looked shifted. Compute the complete foreground+
# shadow ink bounding box and snap it to the actual center of each button.
edit('''            canvas.save();canvas.translate(r.centerX(),r.centerY());
            
            int iconCols=0;for(String line:button.icon)iconCols=Math.max(iconCols,line.length());
            float pixel=Math.max(1,(float)Math.floor(Math.min(r.width()/(iconCols+3f),r.height()/(button.icon.length+3f))));''',
     '''            PixelIconLayout.Placement glyph=PixelIconLayout.place(button.icon,r.left,r.top,r.width(),r.height());
            float pixel=glyph.pixel;''')
edit('''                    float px=(col-(iconCols-1)*.5f)*pixel+offset,py=(row-(button.icon.length-1)*.5f)*pixel+offset;canvas.drawRect(px,py,px+pixel,py+pixel,paint);
                }
            }canvas.restore();''',
     '''                    float px=glyph.x+(col-glyph.minCol)*pixel+offset;
                    float py=glyph.y+(row-glyph.minRow)*pixel+offset;
                    canvas.drawRect(px,py,px+pixel,py+pixel,paint);
                }
            }''')

assert s.count('CallbackBridge.mouseX+=dx;')==2, 'Both primary and second-finger camera routes must deliver deltas'
assert 'if (!MiningDragPolicy.keepAttackWhileLooking(gesture.mining,inputs.held(-1))) {' in s
assert 'if(route.kind==LOOK&&id==lookPointer)lookPointer=-1;' in s
assert 'MinecraftKeyboard.toggleManual()' in s
assert 'JoggerTouchBridge.forceAttackRelease()' in s
assert 'canvas.rotate(' not in s
assert s.count('PixelIconLayout.place(')==1
assert 'worldPointer=-1;lookPointer=-1;lastSlot=-1;' in s
overlay.write_text(s)
for name in ('PixelDirectionIcons.java','PixelIconLayout.java','CameraLookPolicy.java'):
    shutil.copyfile(here/name,java/name)

gradle=root/'app_pojavlauncher/build.gradle'
s=gradle.read_text()
old='versionCode 16300\n        versionName "1.6.3-mining-continuity"'
assert s.count(old)==1
s=s.replace(old,'versionCode 16400\n        versionName "1.6.4-camera-icons"')
gradle.write_text(s)
print('Jogger 1.6.4: independent look finger, no mining camera deadzone, centered icon ink and original-style diagonal heads.')
