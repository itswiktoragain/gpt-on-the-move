#!/usr/bin/env python3
"""1.5.1 safe rollback: ONLY add the on-screen embedded-keyboard toggle.

Apply immediately after orientation-1.5.1. Not a port of mobile-1.5.2+;
no camera, touch, mining, inventory or gameplay logic may be modified.
The build versionCode is high so Android permits an in-place rollback.
"""
from pathlib import Path
import shutil
import sys

root=Path(sys.argv[1]).resolve()
base=Path(__file__).resolve().parent
ov=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java'
kb=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/MinecraftKeyboard.java'
build=root/'app_pojavlauncher/build.gradle'

original={p:p.read_text() for p in (ov,kb,build)}
text=dict(original)
changes={p:[] for p in original}

def replace(path,old,new):
    n=text[path].count(old)
    if n!=1: raise RuntimeError(f'Safe 1.5.1 source drift: {path.name}: expected 1, saw {n} of {old[:100]!r}')
    text[path]=text[path].replace(old,new,1)
    changes[path].append((old,new))

replace(build,
    '        versionCode 15100\n        versionName "1.5.1-mobile"',
    '        versionCode 16800\n        versionName "1.5.1-safe-keyboard"')

replace(ov,
'''        add(MENU,playing()?middle+.5f*w+dp(3):width-safeRight-w,top,w,h,MENU_ICON,null);
        add(BACK,safeLeft,top,w,h,null,"Leave");''',
'''        add(MENU,playing()?middle+.5f*w+dp(3):width-safeRight-w,top,w,h,MENU_ICON,null);
        // Original 1.5.1 controls are untouched; only expose an optional keyboard button.
        add(KEYBOARD,ClassicTopBarLayout.keyboardLeft(width,safeRight,w,dp(6),playing(),mode.equals("CHAT")),top,w,h,TYPE_ICON,null);
        add(BACK,safeLeft,top,w,h,null,"Leave");''')
replace(ov,
    '''        if (playing()) return b.id==CHAT||b.id==PAUSE||b.id==MENU;
        if(mode.equals("CHAT"))return b.id==BACK||b.id==SEND;
        return b.id==BACK||b.id==MENU;''',
    '''        if (playing()) return b.id==CHAT||b.id==PAUSE||b.id==MENU||b.id==KEYBOARD;
        if(mode.equals("CHAT"))return b.id==BACK||b.id==SEND||b.id==KEYBOARD;
        return b.id==BACK||b.id==MENU||b.id==KEYBOARD;''')
replace(ov,
    '        if(id==KEYBOARD){MainActivity.switchKeyboardState();return;}',
    '''        if(id==KEYBOARD){
            // Embedded keyboard only; no touchscreen input/ray/camera changes.
            net.kdt.pojavlaunch.jogger.mobile.MinecraftKeyboard.toggleManual();
            return;
        }''')

replace(kb,
    'private int occupiedHeight;private String gameState="SCREEN";',
    'private int occupiedHeight;private String gameState="SCREEN";private boolean manualOverride;')
replace(kb,
    '''    public static void show(){MinecraftKeyboard k=current();if(k!=null)request(k.owner,true);}
    public static void hide(){MinecraftKeyboard k=current();if(k!=null){k.state.hide();k.apply();}}''',
    '''    /** Dedicated HUD button toggles the existing pixel-art Minecraft keyboard. */
    private void showManually(){
        manualOverride=true;
        manualUntil=Long.MAX_VALUE;
        state.rearm();
        state.show();
        apply();
    }
    public static void show(){
        MinecraftKeyboard k=current();if(k!=null)k.ui.post(k::showManually);
    }
    public static void toggleManual(){
        MinecraftKeyboard k=current();if(k==null)return;
        k.ui.post(()->{
            if(k.state.visible()){
                k.manualOverride=false;k.manualUntil=0;k.state.hide();k.apply();
            }else k.showManually();
        });
    }
    public static void hide(){
        MinecraftKeyboard k=current();
        if(k!=null){k.manualOverride=false;k.manualUntil=0;k.state.hide();k.apply();}
    }''')
replace(kb,
    '''        for(Key key:k.keys)key.invalidate();
        // Only dismiss on screen *transitions*, not on repeated inventory status updates.''',
    '''        // A genuine new game screen resets manual keyboard ownership.
        if(!mode.equals(previous))k.manualOverride=false;
        for(Key key:k.keys)key.invalidate();
        // Only dismiss on screen *transitions*, not on repeated inventory status updates.''')
replace(kb,
    '''        if(mode.equals("PLAY") || (mode.equals("INVENTORY")&&!previous.equals("INVENTORY"))) {
            k.state.hide();k.apply();
        }''',
    '''        if((mode.equals("PLAY")&&!k.manualOverride)
                || (mode.equals("INVENTORY")&&!previous.equals("INVENTORY"))) {
            k.state.hide();k.apply();
        }''')
replace(kb,
    'if(k.latestFocus==token&&SystemClock.uptimeMillis()>=k.manualUntil)',
    'if(k.latestFocus==token&&SystemClock.uptimeMillis()>=k.manualUntil&&!k.manualOverride)')

# PROVE only declared replacements are present; this prevents an accidental
# gameplay patch or silent source drift from passing through this script.
for path,before in original.items():
    expected=before
    for old,new in changes[path]:
        assert expected.count(old)==1
        expected=expected.replace(old,new,1)
    assert expected==text[path], 'Unexpected edits in '+str(path)
    path.write_text(expected)

helper=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicTopBarLayout.java'
if helper.exists():raise RuntimeError('Unexpected top-bar helper already present in 1.5.1')
helper.parent.mkdir(parents=True,exist_ok=True)
shutil.copyfile(base/'ClassicTopBarLayout.java',helper)

# Nothing else is touched: World hit classification, held mouse buttons,
# pointer routing, jump, inventory and native camera are 100% stock 1.5.1.
print('SAFE-ROLLBACK: original 1.5.1 gameplay controls retained; only keyboard toggle added')
print('Versions:', '15100 -> 16800', '1.5.1-mobile -> 1.5.1-safe-keyboard')
print('Modified existing files:', ', '.join(p.name for p in original))
