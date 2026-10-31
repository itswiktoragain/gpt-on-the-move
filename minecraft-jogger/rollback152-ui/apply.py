#!/usr/bin/env python3
"""Jogger Mobile 1.5.9 = 1.5.2 exact base + ONLY 3 UI repairs.

1. World-selection search input via selective obfuscated UI mapping.
2. Real HUD bars positioned at upper left/right, XP and level at top centre.
3. Move existing top launcher buttons back near the top edge.

DO NOT apply mobile-1.5.3 through mobile-1.5.8 source changes.
DO NOT edit picking, camera, gesture, Minecraft-version adapters,
the player's input bindings, inventory clicks, or crosshair.
"""
from pathlib import Path
import shutil,sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent

def edit(path,old,new):
    source=root/path
    text=source.read_text()
    count=text.count(old)
    if count != 1:raise RuntimeError(f"Rollback UI patch drift {source}: {old!r}, found {count}")
    source.write_text(text.replace(old,new,1))

edit("app_pojavlauncher/build.gradle",
     '        versionCode 15200\n        versionName "1.5.2-mobile"',
     '        versionCode 15900\n        versionName "1.5.9-rollback152"')

# Only the UI mapping companion is new; the 1.5.2 agent touch mapping
# keys are explicitly kept intact by UiMappingFilter.merge().
edit("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/JoggerTouchBridge.java",
     'File mapping = net.kdt.pojavlaunch.jogger.classic.GameAssets.mappings(context);',
     'File mapping = net.kdt.pojavlaunch.jogger.mobile.UiMapping.mappings(context);')

# The 1.5.2 addition placed buttons 44 dp too low. Restore top clearance,
# while leaving all button positions, button handlers and touch routing alone.
edit("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java",
     'float controlsTop=playing()?top+dp(44):top;',
     'float controlsTop=top+dp(2);')

# The existing 1.5.2 status rendering is correct in principle but lacked
# UI mappings. Some armour/food helpers also execute without a parent health
# group: ensure their own top Y translation is applied in that situation.
hooks="jogger_touch_agent/src/main/java/com/minecraftjogger/touch/MobileHooks.java"
edit(hooks,
     '''                HudLayout.Shift shift=HudLayout.shift(kind,width,height);
                dx=shift.x;dy=shift.y;''',
     '''                HudLayout.Shift shift=HudLayout.shift(kind,width,height);
                dx=shift.x;dy=shift.y;
                if(kind==5||kind==6){
                    boolean insideHealth=false;
                    for(Transform parent:transforms.get())
                        if(parent!=frame&&parent.kind==4){insideHealth=true;break;}
                    if(!insideHealth)dy=-height+95;
                }''')

hud=root/"jogger_touch_agent/src/main/java/com/minecraftjogger/touch/HudLayout.java"
original=hud.read_text()
for old,new,count in [("-height+56","-height+95",2),("-height+44","-height+77",1),("-height+73","-height+107",1)]:
    if original.count(old)!=count:
        raise RuntimeError(f"Unexpected 1.5.2 HUD layout {old}: {original.count(old)}")
    original=original.replace(old,new)
hud.write_text(original)

tests=root/"scripts/mobile16-tests/HudLayoutTest.java"
text=tests.read_text()
for old,new,count in [("-259","-220",2),("-271","-238",1),("-242","-208",1)]:
    if text.count(old)!=count:raise RuntimeError(f"Unexpected HUD test {old}: {text.count(old)}")
    text=text.replace(old,new)
tests.write_text(text)

dest=root/"app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile"
for filename in ["UiMapping.java","UiMappingFilter.java"]:
    shutil.copyfile(here/filename,dest/filename)
asset=root/"app_pojavlauncher/src/main/assets/jogger-ui-only-1.21.4.properties"
asset.parent.mkdir(parents=True,exist_ok=True)
shutil.copyfile(here/"ui-only-1.21.4.properties",asset)

# Regression guard: none of the later camera/aiming workarounds may sneak into
# this 1.5.2 baseline, and release the mining button on pointer-up as before.
touch=(root/"app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java").read_text()
for forbidden in ["aimLegacy(","if(!gesture.mining)gesture.move(x,y);"]:
    if forbidden in touch:raise RuntimeError(f"Later controls regression leaked into rollback: {forbidden}")
for baseline in ["gesture.move(x,y);","gesture.begin(x,y,event.getEventTime(),dp(8));target(x,y,true);",
                 "pendingHold=-1;inputs.set(-3);worldPointer=-1;"]:
    if baseline not in touch:raise RuntimeError(f"1.5.2 touch behavior unexpectedly missing: {baseline}")

print("Jogger 1.5.9: rebased EXACTLY on 1.5.2 + world-search, real HUD and top-button fixes only.")
