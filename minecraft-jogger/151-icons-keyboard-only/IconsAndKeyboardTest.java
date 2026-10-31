import net.kdt.pojavlaunch.jogger.classic.PixelDirectionIcons;
import net.kdt.pojavlaunch.jogger.classic.PixelIconLayout;
import java.nio.file.*;
import java.util.*;

/** Pure UI tests; intentionally does NOT change gameplay code or agent. */
public class IconsAndKeyboardTest {
    static int tests=0;
    static void ok(boolean value,String explanation){if(!value)throw new AssertionError(explanation);tests++;}
    static int weight(String[] sprite){int n=0;for(String row:sprite)for(char c:row.toCharArray())if(c=='1')n++;return n;}
    static String[] rotate(String[] sprite){String[] next=new String[7];for(int y=0;y<7;y++){StringBuilder row=new StringBuilder();for(int x=0;x<7;x++)row.append(sprite[6-x].charAt(y));next[y]=row.toString();}return next;}
    static void checkVisual(String[] sprite, float x,float y,float width,float height) {
        PixelIconLayout.Placement p=PixelIconLayout.place(sprite,x,y,width,height);
        ok(p.pixel>0,"Icon pixels must be visible");
        ok(Math.abs(p.visualCenterX()-(x+width/2))<=.51f,"Visual center X must be centered incl shadow");
        ok(Math.abs(p.visualCenterY()-(y+height/2))<=.51f,"Visual center Y must be centered incl shadow");
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Pass 1.5.1 ClassicOverlay.java path");
        String src=Files.readString(Path.of(args[0]));
        for(String[] a:new String[][]{PixelDirectionIcons.UP,PixelDirectionIcons.DOWN,PixelDirectionIcons.LEFT,PixelDirectionIcons.RIGHT,PixelDirectionIcons.UP_LEFT,PixelDirectionIcons.UP_RIGHT}){
            ok(a.length==7,"Sprite height 7");
            for(String r:a)ok(r.length()==7,"Sprite width 7");
            ok(weight(a)==19,"All arrows equal ink weight");
            for(float d:new float[]{34,45,53,70})checkVisual(a,12,17,d,d);
        }
        ok(Arrays.equals(rotate(PixelDirectionIcons.UP),PixelDirectionIcons.RIGHT),"RIGHT clockwise from UP");
        ok(Arrays.equals(rotate(PixelDirectionIcons.RIGHT),PixelDirectionIcons.DOWN),"DOWN clockwise from RIGHT");
        ok(Arrays.equals(rotate(PixelDirectionIcons.DOWN),PixelDirectionIcons.LEFT),"LEFT clockwise from DOWN");
        for(int y=0;y<7;y++){
            ok(PixelDirectionIcons.UP_LEFT[y].equals(new StringBuilder(PixelDirectionIcons.UP_RIGHT[y]).reverse().toString()),"Diagonal heads mirrored");
            if(y>=5)ok(PixelDirectionIcons.UP_LEFT[y].replace("0","").length()==1,"One-pixel diagonal shafts");
        }
        ok(src.contains("if(id==KEYBOARD){MainActivity.switchKeyboardState();return;}"),"Native 1.5.1 keyboard handler used, no replacement");
        ok(src.contains("add(KEYBOARD,playing()?width-safeRight-w:width-safeRight-2*w-dp(6),top,w,h,TYPE_ICON,null)"),"Keyboard button positioned only in UI");
        ok(src.contains("b.id==BACK||b.id==SEND||b.id==KEYBOARD"),"Keyboard visible in chat");
        ok(src.contains("b.id==CHAT||b.id==PAUSE||b.id==MENU||b.id==KEYBOARD"),"Keyboard visible in gameplay");
        ok(src.contains("return b.id==BACK||b.id==MENU||b.id==KEYBOARD"),"Keyboard visible in menus");
        ok(src.contains("gesture.begin(x,y,event.getEventTime(),dp(8))"),"Original 1.5.1 gesture untouched");
        ok(src.contains("handler.removeCallbacks(hold);pendingHold=-1;inputs.set(-3);target(0,0,false);"),"Original 1.5.1 mining/drag untouched");
        ok(src.contains("if(request!=pendingHold||stopped||!playing()||worldPointer<0||!gesture.active||!gesture.mining||gesture.moved)return;"),"Original 1.5.1 hold gating untouched");
        ok(src.contains("private boolean playing() { return CallbackBridge.isGrabbing() && mode.equals(\"PLAY\"); }"),"Original 1.5.1 camera state untouched");
        ok(!src.contains("TouchRoutingPolicy")&&!src.contains("WorldGesturePolicy")&&!src.contains("CameraLookPolicy"),"No late camera classes");
        ok(!src.contains("toggleManual"),"No later manual keyboard override");
        ok(!src.contains("canvas.rotate("),"New arrow sprites never rotated on canvas");
        System.out.println("IconsAndKeyboardTest: "+tests+" checks passed. ORIGINAL 1.5.1 GAMEPLAY PRESERVED.");
    }
}
