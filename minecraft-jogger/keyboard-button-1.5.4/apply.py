#!/usr/bin/env python3
"""Jogger Mobile 1.5.4: restore a real, always-available Minecraft keyboard toggle.

Runs after mobile-1.5.3/apply.py. Each source edit has a unique anchor so builds
fail rather than silently shipping an older overlay.
"""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent

def replace(path, original, replacement):
    file = root / path
    before = file.read_text()
    if before.count(original) != 1:
        raise RuntimeError(f"Keyboard toggle source drift in {path}: expected 1 anchor, saw {before.count(original)}")
    file.write_text(before.replace(original, replacement, 1))

replace(
    "app_pojavlauncher/build.gradle",
    '        versionCode 15300\n        versionName "1.5.3-mobile"',
    '        versionCode 15400\n        versionName "1.5.4-mobile"'
)

overlay = "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java"

replace(
    overlay,
    '''        add(MENU,playing()?middle+.5f*w+dp(3):width-safeRight-w,controlsTop,w,h,MENU_ICON,null);
        add(BACK,safeLeft,top,w,h,null,"Leave");''',
    '''        add(MENU,playing()?middle+.5f*w+dp(3):width-safeRight-w,controlsTop,w,h,MENU_ICON,null);
        // In menus, immediately left of Menu; in gameplay, at the far-right
        // corner previously occupied by the menu-screen Menu button.
        // In chat it stays left of Send to avoid overlapping Send.
        add(KEYBOARD,ClassicTopBarLayout.keyboardLeft(width,safeRight,w,dp(6),playing(),mode.equals("CHAT")),
                controlsTop,w,h,TYPE_ICON,null);
        add(BACK,safeLeft,top,w,h,null,"Leave");'''
)
replace(
    overlay,
    '''        if (playing()) return b.id==CHAT||b.id==PAUSE||b.id==MENU;
        if(mode.equals("CHAT"))return b.id==BACK||b.id==SEND;
        return b.id==BACK||b.id==MENU;''',
    '''        if (playing()) return b.id==CHAT||b.id==PAUSE||b.id==MENU||b.id==KEYBOARD;
        if(mode.equals("CHAT"))return b.id==BACK||b.id==SEND||b.id==KEYBOARD;
        return b.id==BACK||b.id==MENU||b.id==KEYBOARD;'''
)
replace(
    overlay,
    '        if(id==KEYBOARD){MainActivity.switchKeyboardState();return;}',
    '''        if(id==KEYBOARD){
            // Do not depend on Android IME focus or the unreliable automatic
            // game-text-field detector; open the embedded Minecraft keyboard.
            net.kdt.pojavlaunch.jogger.mobile.MinecraftKeyboard.toggleManual();
            return;
        }'''
)

keyboard = "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/MinecraftKeyboard.java"
replace(
    keyboard,
    'private int occupiedHeight;private String gameState="SCREEN";private boolean manualChatOpen;',
    'private int occupiedHeight;private String gameState="SCREEN";private boolean manualChatOpen,manualOverride;'
)
replace(
    keyboard,
    '''    public static void show(){MinecraftKeyboard k=current();if(k!=null)request(k.owner,true);}
    public static void hide(){MinecraftKeyboard k=current();if(k!=null){k.manualChatOpen=false;k.state.hide();k.apply();}}''',
    '''    private void displayManually(){
        manualOverride=true;
        manualUntil=Long.MAX_VALUE;
        state.rearm();
        state.show();
        apply();
    }
    /** Manual fallback, deliberately independent of Minecraft text-field focus. */
    public static void show(){
        MinecraftKeyboard k=current();
        if(k!=null)k.ui.post(k::displayManually);
    }
    /** Dedicated HUD keyboard key; tapping again hides the keyboard. */
    public static void toggleManual(){
        MinecraftKeyboard k=current();if(k==null)return;
        k.ui.post(()->{
            if(k.state.visible()){
                k.manualOverride=false;k.manualChatOpen=false;k.manualUntil=0;
                k.state.hide();k.apply();
            }else k.displayManually();
        });
    }
    public static void hide(){
        MinecraftKeyboard k=current();
        if(k!=null){k.manualOverride=false;k.manualChatOpen=false;k.manualUntil=0;k.state.hide();k.apply();}
    }'''
)
replace(
    keyboard,
    '''        if(!mode.equals("CHAT"))k.manualChatOpen=false;
        for(Key key:k.keys)key.invalidate();''',
    '''        if(!mode.equals("CHAT"))k.manualChatOpen=false;
        // A repeated PLAY status packet must not immediately dismiss a
        // keyboard explicitly opened by the HUD. Real screen changes do.
        if(!mode.equals(previous))k.manualOverride=false;
        for(Key key:k.keys)key.invalidate();'''
)
replace(
    keyboard,
    '''        if(mode.equals("PLAY") || (mode.equals("INVENTORY")&&!previous.equals("INVENTORY"))) {
            k.state.hide();k.apply();
        }''',
    '''        if((mode.equals("PLAY")&&!k.manualOverride)
                || (mode.equals("INVENTORY")&&!previous.equals("INVENTORY"))) {
            k.state.hide();k.apply();
        }'''
)
replace(
    keyboard,
    'if(k.latestFocus==token&&SystemClock.uptimeMillis()>=k.manualUntil&&!k.manualChatOpen)',
    'if(k.latestFocus==token&&SystemClock.uptimeMillis()>=k.manualUntil&&!k.manualChatOpen&&!k.manualOverride)'
)

out = root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicTopBarLayout.java"
out.parent.mkdir(parents=True,exist_ok=True)
shutil.copyfile(here / "ClassicTopBarLayout.java",out)
print("Jogger 1.5.4 applied: manual Minecraft keyboard button, position and persistent manual toggle")
