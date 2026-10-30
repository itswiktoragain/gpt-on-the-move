#!/usr/bin/env python3
"""Apply the checksum-verified source diff after the existing Mobile 1.2 overlay.
The decoded, reviewable diff is included in the build artifact and source download.
"""
import base64,hashlib,lzma,pathlib,subprocess,sys
here=pathlib.Path(__file__).resolve().parent
encoded=''.join((here/('patch.%d.b64'%i)).read_text().strip() for i in range(3))
data=base64.b64decode(encoded,validate=True)
if hashlib.sha256(data).hexdigest()!='22bea97ade7d26360ed1e7028125daa6354b98f7514c1f660f441e014f463539':
 raise RuntimeError('Keyboard/icon source patch checksum mismatch')
patch=lzma.decompress(data)
(here/'keyboard-icons.patch').write_bytes(patch)
root=pathlib.Path(sys.argv[1]).resolve()
subprocess.run(['git','apply','--check','-'],input=patch,cwd=root,check=True)
subprocess.run(['git','apply','-'],input=patch,cwd=root,check=True)
# Intermediary aliases are also constrained by class and method signature.
p=root/'jogger_touch_agent/src/main/java/com/minecraftjogger/touch/TextInputProbe.java'
s=p.read_text().replace('"EditBox","TextFieldWidget","GuiTextField","TextField"','"EditBox","TextFieldWidget","GuiTextField","TextField","net.minecraft.class_342"')
s=s.replace('"getFocused","getFocusedChild","getFocusedWidget"','"getFocused","getFocusedChild","getFocusedWidget","method_25399"')
s=s.replace('"isFocused","isTextFieldFocused"','"isFocused","isTextFieldFocused","method_25370"')
p.write_text(s)
# Screen identity stays stable when Minecraft recreates its widgets during viewport resize.
app=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch'
agent=root/'jogger_touch_agent/src/main/java/com/minecraftjogger/touch'
def swap(p,a,b):
 s=p.read_text()
 if a not in s:raise RuntimeError('Missing refinement anchor in '+str(p)+': '+a[:100])
 p.write_text(s.replace(a,b))
(app/'jogger/mobile/KeyboardState.java').write_text('''package net.kdt.pojavlaunch.jogger.mobile;
/** Hide survives GUI resize/recreated fields; a new screen or explicit tap can reopen. */
public final class KeyboardState {
    private long focus=-1, screen=-1, dismissed=-2, dismissedScreen=-2;
    private boolean visible;
    public boolean report(long token,boolean active){return report(token,token,active);}
    public boolean report(long token,long screenToken,boolean active) {
        if(token<focus)return visible;
        focus=token;screen=screenToken;
        visible=active && token!=dismissed && screenToken!=dismissedScreen;
        return visible;
    }
    public void rearm(){dismissed=dismissedScreen=-2;}
    public void show(){rearm();visible=true;}
    public void hide(){dismissed=focus;dismissedScreen=screen;visible=false;}
    public boolean visible(){return visible;}
}
''')
swap(agent/'TextInputProbe.java','public final long token; public final boolean active;','public final long token,screen; public final boolean active;')
swap(agent/'TextInputProbe.java','State(long token, boolean active) { this.token=token; this.active=active; }','State(long token,long screen, boolean active) { this.token=token; this.screen=screen; this.active=active; }')
swap(agent/'TextInputProbe.java','new State(0,false)','new State(0,0,false)')
swap(agent/'TextInputProbe.java','lastScreen=screen;lastTarget=target;state=new State(old.token+1,target!=null);','long screenToken=old.screen+(screen!=lastScreen?1:0);lastScreen=screen;lastTarget=target;state=new State(old.token+1,screenToken,target!=null);')
swap(agent/'ClassicGameHooks.java','input.token+" "+(input.active?1:0)','input.token+" "+input.screen+" "+(input.active?1:0)')
p=app/'jogger/JoggerTouchBridge.java'
swap(p,'if ("I".equals(f[0]) && f.length == 4)','if ("I".equals(f[0]) && f.length == 5)')
swap(p,'long focus=Long.parseLong(f[2]);\n                if(focus<0 || !("0".equals(f[3])||"1".equals(f[3])))return;','long focus=Long.parseLong(f[2]), screenToken=Long.parseLong(f[3]);\n                if(focus<0 || screenToken<0 || !("0".equals(f[4])||"1".equals(f[4])))return;')
swap(p,'MinecraftKeyboard.report(focus,"1".equals(f[3]))','MinecraftKeyboard.report(focus,screenToken,"1".equals(f[4]))')
p=app/'jogger/mobile/MinecraftKeyboard.java'
swap(p,'public static void report(long token,boolean active)','public static void report(long token,long screenToken,boolean active)')
swap(p,'k.state.report(token,true)','k.state.report(token,screenToken,true)')
swap(p,'k.state.report(token,false)','k.state.report(token,screenToken,false)')
swap(p,'    public static void show(){','    public static void gameTap(){MinecraftKeyboard k=current();if(k!=null)k.state.rearm();net.kdt.pojavlaunch.jogger.JoggerTouchBridge.requestTextFocus();}\n    public static void show(){')
swap(app/'customcontrols/mouse/InGUIEventProcessor.java','net.kdt.pojavlaunch.jogger.JoggerTouchBridge::requestTextFocus','net.kdt.pojavlaunch.jogger.mobile.MinecraftKeyboard::gameTap')
p=root/'scripts/keyboard-tests/KeyboardFocusTest.java'
swap(p,'getDeclaredFields().length==2','getDeclaredFields().length==3')
swap(p,'        System.out.println("KeyboardFocusTest:', '''        KeyboardState resized=new KeyboardState();check(resized.report(100,1,true),"Initial field opens");resized.hide();
        check(!resized.report(101,1,true),"Resize must not reopen manually hidden keyboard");
        resized.rearm();check(resized.report(102,1,true),"Explicit re-tap may reopen the same screen");
        resized.hide();check(resized.report(103,2,true),"A new text screen must open");
        check(!resized.report(104,3,false),"Non-text screen hides");
        System.out.println("KeyboardFocusTest:''')
print('Applied Mobile 1.3: themed in-game keyboard, automatic focus, resize-safe Hide, custom/grass icons and shortcut refresh.')
