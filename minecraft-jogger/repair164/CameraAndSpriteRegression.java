import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.kdt.pojavlaunch.jogger.classic.CameraLookPolicy;
import net.kdt.pojavlaunch.jogger.classic.PixelIconLayout;
import net.kdt.pojavlaunch.jogger.classic.PixelDirectionIcons;

/** Regression for multi-touch camera, sub-slop mining look and icon geometry. */
public final class CameraAndSpriteRegression {
    private static int checks;
    private static void ok(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    private static int pixels(String[] image){int total=0;for(String row:image)for(char c:row.toCharArray())if(c=='1')total++;return total;}
    private static String[] rotated(String[] image){String[] output=new String[image.length];for(int y=0;y<image.length;y++){StringBuilder line=new StringBuilder();for(int x=0;x<image.length;x++)line.append(image[image.length-1-x].charAt(y));output[y]=line.toString();}return output;}
    private static void checkArrows(){
        String[][] cardinal={PixelDirectionIcons.UP,PixelDirectionIcons.RIGHT,PixelDirectionIcons.DOWN,PixelDirectionIcons.LEFT};
        for(int i=0;i<4;i++){
            ok(Arrays.equals(rotated(cardinal[i]),cardinal[(i+1)%4]),"Cardinal arrow keeps its exact original pixel art at 90-degree increments");
            ok(pixels(cardinal[i])==19,"Cardinal arrow ink weight 19");
        }
        String[] ul=PixelDirectionIcons.UP_LEFT,ur=PixelDirectionIcons.UP_RIGHT;
        ok(pixels(ul)==19&&pixels(ur)==19,"Diagonal arrowheads match original arrow ink weight");
        for(int y=0;y<7;y++) {
            ok(ul[y].length()==7&&ur[y].length()==7,"Matching 7x7 sprite resolution");
            for(int x=0;x<7;x++){
                ok(ul[y].charAt(x)==ur[y].charAt(6-x),"UL and UR are mirrored without canvas rotation");
                ok(ul[y].charAt(x)==ul[x].charAt(y),"Diagonal arrowhead symmetric across NW shaft axis");
            }
            for(int x=0;x<7;x++){
                boolean wing=x+y<=4;
                boolean shaft=x==y&&x>=3;
                ok((ul[y].charAt(x)=='1')==(wing||shaft),"Filled triangular arrowhead plus 1-cell shaft geometry");
            }
        }
        ok(ul[5].replace("0","").length()==1&&ul[6].replace("0","").length()==1,"NW shaft width exactly 1 below head");
        ok(ur[5].replace("0","").length()==1&&ur[6].replace("0","").length()==1,"NE shaft width exactly 1 below head");
    }
    private static void centered(String[] icon,float x,float y,float w,float h,String label){
        PixelIconLayout.Placement p=PixelIconLayout.place(icon,x,y,w,h);
        ok(p.pixel>=1,"Nonzero device pixel cells "+label);
        ok(Math.abs(p.visualCenterX()-(x+w*.5f))<=.51f,"X icon+shadow centered "+label);
        ok(Math.abs(p.visualCenterY()-(y+h*.5f))<=.51f,"Y icon+shadow centered "+label);
        ok(p.x>=x-1&&p.y>=y-1,"Icon remains in button "+label);
        ok(p.x+(p.inkWidth+1)*p.pixel <=x+w+1,"Icon fits button width "+label);
        ok(p.y+(p.inkHeight+1)*p.pixel<=y+h+1,"Icon fits button height "+label);
    }
    private static void checkGeometry(){
        String[][] icons={PixelDirectionIcons.UP,PixelDirectionIcons.LEFT,PixelDirectionIcons.RIGHT,PixelDirectionIcons.DOWN,PixelDirectionIcons.UP_LEFT,PixelDirectionIcons.UP_RIGHT,
          new String[]{"011111110","110000011","100000001","101010101","100000001","111111111","001100000","001000000"},
          new String[]{"00000000","00000000","00000000","11011011","11011011","00000000","00000000"},
          new String[]{"0001000","0010100","0100010","1000001","0100010","0010100","0001000"}};
        float[][] rectangles={{12,17,47,47},{3.25f,4.75f,59,39},{45,23,72,72},{0,0,34,34},{108.2f,170.7f,58.3f,42.5f}};
        for(int i=0;i<icons.length;i++)for(float[] r:rectangles)
            centered(icons[i],r[0],r[1],r[2],r[3],"icon "+i);
        String[] leftPad={"000000","001000","001000","001000","000000"};
        centered(leftPad,0,0,50,50,"visibly off-center declared glyph");
    }
    private static void checkCamera(String source){
        ok(!CameraLookPolicy.shouldRotate(false,false,false),"Stationary tap must not turn");
        ok(CameraLookPolicy.shouldRotate(true,false,false),"Normal drag rotates view");
        ok(CameraLookPolicy.shouldRotate(false,true,true),"Mining drag smaller than slop rotates view");
        ok(!CameraLookPolicy.shouldRotate(false,true,false),"Unheld stale mining state does not rotate");
        ok(CameraLookPolicy.routeSecondLook(true,true,false),"Second world finger becomes independent camera look");
        ok(!CameraLookPolicy.routeSecondLook(true,true,true),"No duplicate look pointer");
        ok(!CameraLookPolicy.routeSecondLook(false,true,false),"No world look in menus");
        ok(!CameraLookPolicy.routeSecondLook(true,false,false),"No look pointer without a primary world finger");
        int begin=source.indexOf("if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)");
        int move=source.indexOf("if(action==MotionEvent.ACTION_MOVE)",begin);
        int up=source.indexOf("if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)",move);
        ok(begin>0&&move>begin&&up>move,"Down/move/up touch route order");
        String down=source.substring(begin,move),motion=source.substring(move,up);
        ok(down.contains("routes.put(id,new Route(LOOK,null));lookPointer=id;lookX=x;lookY=y;"),"Second finger receives independent look route");
        ok(motion.contains("route.kind==LOOK&&pointer==lookPointer&&playing()"),"Independent second finger is processed on MOVE");
        ok(motion.contains("float dx=(x-lookX)*factor,dy=(y-lookY)*factor"),"Second finger sends actual movement delta");
        ok(motion.contains("lookX=x;lookY=y;"),"Look pointer updates position each MOVE");
        ok(motion.contains("shouldRotate(gesture.moved,gesture.mining,inputs.held(-1))"),"Primary finger can turn during mining with no 8dp threshold");
        ok(motion.contains("if (!MiningDragPolicy.keepAttackWhileLooking(gesture.mining,inputs.held(-1)))"),"Held mining attack stays pressed while turning");
        ok(source.contains("if(route.kind==LOOK&&id==lookPointer)lookPointer=-1;"),"Second look pointer releases independently");
        ok(source.contains("worldPointer=-1;lookPointer=-1;lastSlot=-1;"),"Cancel clears both world pointers");
        ok(source.contains("if(!inputs.held(-1))JoggerTouchBridge.forceAttackRelease()"),"Release protocol remains in place");
        ok(source.contains("getParent().requestDisallowInterceptTouchEvent(true)"),"Parent cannot steal ongoing look gesture");
        ok(source.contains("PixelIconLayout.place(button.icon,r.left,r.top,r.width(),r.height())"),"Every control icon goes through centered renderer");
        ok(source.contains("glyph.x+(col-glyph.minCol)*pixel+offset")&&source.contains("glyph.y+(row-glyph.minRow)*pixel+offset"),"All glyphs use tight ink origin");
        ok(!source.contains("canvas.rotate("),"No canvas rotation can skew icon shadows");
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("pass assembled ClassicOverlay.java");
        String src=new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8);
        checkArrows();checkGeometry();checkCamera(src);
        System.out.println("CameraAndSpriteRegression: "+checks+" checks passed");
    }
}
