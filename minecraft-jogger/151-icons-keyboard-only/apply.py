#!/usr/bin/env python3
"""Clean-room UI-only addition to EXACT Minecraft Jogger 1.5.1 source.

Only changes: version code, top-row KEYBOARD button, directional icon masks,
and decorative pixel rendering. No touch, camera, mining, or agent modifications.
"""
from pathlib import Path
import hashlib,shutil,sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
source=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic'
overlay=source/'ClassicOverlay.java'
version=root/'app_pojavlauncher/build.gradle'
text=overlay.read_text()
original=text
expected_sha256='c8ce1cee9db942049117f2438fa3d9c7fca4c41f44a2cb41448bacc375b81c3c'
assert hashlib.sha256(overlay.read_bytes()).hexdigest()==expected_sha256, 'NOT the original 1.5.1 source. Refusing changes.'

def edit(before,after):
    global text
    n=text.count(before)
    if n!=1:raise RuntimeError(f'Exact 1.5.1 UI anchor not found ({n} matches): {before[:150]}')
    text=text.replace(before,after,1)

# The KEYBOARD button ALREADY exists in the 1.5.1 click() handler and
# delegates to MainActivity.switchKeyboardState(). Only expose it as UI.
assert 'if(id==KEYBOARD){MainActivity.switchKeyboardState();return;}' in original
edit('''        add(MENU,playing()?middle+.5f*w+dp(3):width-safeRight-w,top,w,h,MENU_ICON,null);
        add(BACK,safeLeft,top,w,h,null,"Leave");''',
'''        add(MENU,playing()?middle+.5f*w+dp(3):width-safeRight-w,top,w,h,MENU_ICON,null);
        // 1.5.1 already implements the keyboard action; show its HUD button.
        add(KEYBOARD,playing()?width-safeRight-w:width-safeRight-2*w-dp(6),top,w,h,TYPE_ICON,null);
        add(BACK,safeLeft,top,w,h,null,"Leave");''')
edit('''        if (playing()) return b.id==CHAT||b.id==PAUSE||b.id==MENU;
        if(mode.equals("CHAT"))return b.id==BACK||b.id==SEND;
        return b.id==BACK||b.id==MENU;''',
'''        if (playing()) return b.id==CHAT||b.id==PAUSE||b.id==MENU||b.id==KEYBOARD;
        if(mode.equals("CHAT"))return b.id==BACK||b.id==SEND||b.id==KEYBOARD;
        return b.id==BACK||b.id==MENU||b.id==KEYBOARD;''')

# Pure icon art only: use unrotated pixel art to retain 1-cell diagonal shafts.
# No movement, touch hitbox, camera sensitivity or action logic is changed.
edit('''        add(UL,x,y,cell-gap,cell-gap,ARROW,null);add(UP,x+cell,y,cell-gap,cell-gap,ARROW,null);add(UR,x+2*cell,y,cell-gap,cell-gap,ARROW,null);
        add(LEFT,x,y+cell,cell-gap,cell-gap,ARROW,null);add(CROUCH,x+cell,y+cell,cell-gap,cell-gap,PERSON,null);add(RIGHT,x+2*cell,y+cell,cell-gap,cell-gap,ARROW,null);
        add(DOWN,x+cell,y+2*cell,cell-gap,cell-gap,ARROW,null);''',
'''        add(UL,x,y,cell-gap,cell-gap,PixelDirectionIcons.UP_LEFT,null);add(UP,x+cell,y,cell-gap,cell-gap,PixelDirectionIcons.UP,null);add(UR,x+2*cell,y,cell-gap,cell-gap,PixelDirectionIcons.UP_RIGHT,null);
        add(LEFT,x,y+cell,cell-gap,cell-gap,PixelDirectionIcons.LEFT,null);add(CROUCH,x+cell,y+cell,cell-gap,cell-gap,PERSON,null);add(RIGHT,x+2*cell,y+cell,cell-gap,cell-gap,PixelDirectionIcons.RIGHT,null);
        add(DOWN,x+cell,y+2*cell,cell-gap,cell-gap,PixelDirectionIcons.DOWN,null);''')
edit('''            canvas.save();canvas.translate(r.centerX(),r.centerY());
            if(button.id==LEFT)canvas.rotate(-90);else if(button.id==RIGHT)canvas.rotate(90);else if(button.id==DOWN)canvas.rotate(180);else if(button.id==UL)canvas.rotate(-45);else if(button.id==UR)canvas.rotate(45);
            int iconCols=0;for(String line:button.icon)iconCols=Math.max(iconCols,line.length());
            float pixel=Math.max(1,(float)Math.floor(Math.min(r.width()/(iconCols+3f),r.height()/(button.icon.length+3f))));
            for(int pass=0;pass<2;pass++) {
                paint.setColor(pass==0?0xff202020:0xfff4f4f4);float offset=pass==0?pixel*.5f:0;
                for(int row=0;row<button.icon.length;row++)for(int col=0;col<button.icon[row].length();col++)if(button.icon[row].charAt(col)=='1') {
                    float px=(col-iconCols/2f)*pixel+offset,py=(row-button.icon.length/2f)*pixel+offset;canvas.drawRect(px,py,px+pixel,py+pixel,paint);
                }
            }canvas.restore();''',
'''            PixelIconLayout.Placement glyph=PixelIconLayout.place(button.icon,r.left,r.top,r.width(),r.height());
            float pixel=glyph.pixel;
            for(int pass=0;pass<2;pass++) {
                paint.setColor(pass==0?0xff202020:0xfff4f4f4);float offset=pass==0?pixel:0;
                for(int row=0;row<button.icon.length;row++)for(int col=0;col<button.icon[row].length();col++)if(button.icon[row].charAt(col)=='1') {
                    float px=glyph.x+(col-glyph.minCol)*pixel+offset;
                    float py=glyph.y+(row-glyph.minRow)*pixel+offset;
                    canvas.drawRect(px,py,px+pixel,py+pixel,paint);
                }
            }''')

# EXTRA SAFETY: gameplay code must remain an exact substring of original.
for start,end in (
    ('    @SuppressLint("ClickableViewAccessibility")','    @Override public boolean performClick()'),
    ('    private void press(int pointer,Route route,boolean fresh)','    private void stopSprintWhenIdle()'),
    ('    private static long tapSequence;','    private void hideKeyboard()'),
    ('    private boolean targetEnabled()','\n}'),
):
    old=original[original.index(start):original.index(end,original.index(start))]
    new=text[text.index(start):text.index(end,text.index(start))]
    assert old==new,'Attempted to modify 1.5.1 gameplay controls at '+start

for new_class in ('PixelDirectionIcons.java','PixelIconLayout.java'):
    assert not (source/new_class).exists(),'Unexpected later icon class in original 1.5.1'
    shutil.copyfile(here/new_class,source/new_class)
overlay.write_text(text)
b=version.read_text()
old='versionCode 15100\n        versionName "1.5.1-mobile"'
assert b.count(old)==1,'Missing exact 1.5.1 version code'
version.write_text(b.replace(old,'versionCode 16900\n        versionName "1.5.1-clean-icons-keyboard"',1))
print('VERIFIED clean 1.5.1 source: gameplay code unchanged, only icons + existing keyboard button exposed.')
