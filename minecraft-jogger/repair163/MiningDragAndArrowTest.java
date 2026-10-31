import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.kdt.pojavlaunch.jogger.classic.ClassicLogic;
import net.kdt.pojavlaunch.jogger.classic.MiningDragPolicy;
import net.kdt.pojavlaunch.jogger.classic.PixelDirectionIcons;

/** Real ownership / gesture transitions plus source-level wiring and pixel-mask checks. */
public final class MiningDragAndArrowTest {
    private static int checks;
    private static void ok(boolean cond, String what) {
        if(!cond)throw new AssertionError(what);
        checks++;
    }
    private static class Events implements ClassicLogic.Output {
        final ArrayList<String> events = new ArrayList<>();
        @Override public void key(int key,boolean down){ events.add("key"+key+":"+down); }
        @Override public void mouse(int button,boolean down){ events.add("mouse"+button+":"+down); }
    }
    private static String[] clockwise(String[] in) {
        String[] out=new String[in.length];
        for(int y=0;y<in.length;y++) {
            StringBuilder b=new StringBuilder();
            for(int x=0;x<in.length;x++)b.append(in[in.length-1-x].charAt(y));
            out[y]=b.toString();
        }
        return out;
    }
    private static void same(String[] a,String[] b,String message) {
        ok(Arrays.equals(a,b),message);
    }
    private static void checkIcons() {
        same(PixelDirectionIcons.RIGHT,clockwise(PixelDirectionIcons.UP),"RIGHT is true raster rotation of UP");
        same(PixelDirectionIcons.DOWN,clockwise(PixelDirectionIcons.RIGHT),"DOWN rotated from RIGHT");
        same(PixelDirectionIcons.LEFT,clockwise(PixelDirectionIcons.DOWN),"LEFT rotated from DOWN");
        same(PixelDirectionIcons.UP,clockwise(PixelDirectionIcons.LEFT),"cardinal rotation cycle exact");
        String[][] glyphs={PixelDirectionIcons.UP,PixelDirectionIcons.DOWN,PixelDirectionIcons.LEFT,
            PixelDirectionIcons.RIGHT,PixelDirectionIcons.UP_LEFT,PixelDirectionIcons.UP_RIGHT};
        for(String[] glyph:glyphs) {
            ok(glyph.length==7,"All arrow glyphs are 7 cells high");
            for(String row:glyph)ok(row.matches("[01]{7}"),"Only seven square bitmap cells per row");
        }
        String[] a=PixelDirectionIcons.UP_LEFT, b=PixelDirectionIcons.UP_RIGHT;
        for(int y=0;y<7;y++) {
            for(int x=0;x<7;x++)ok(a[y].charAt(x)==b[y].charAt(6-x),"Diagonal arrow mirrored exactly");
            if(y>=4) {
                ok(a[y].charAt(y)=='1',"NW shaft on single diagonal at row "+y);
                ok(b[y].charAt(6-y)=='1',"NE shaft on single diagonal at row "+y);
                ok(a[y].replace("0","").length()==1,"NW shaft EXACTLY one cell wide at row "+y);
                ok(b[y].replace("0","").length()==1,"NE shaft EXACTLY one cell wide at row "+y);
            }
        }
        ok(a[0].startsWith("11111") && b[0].endsWith("11111"),"Blocky arrowhead kept");
    }
    private static void checkGesture() {
        Events e=new Events();
        ClassicLogic.Inputs inputs=new ClassicLogic.Inputs(e);
        ClassicLogic.Gesture g=new ClassicLogic.Gesture();
        g.begin(100,100,1000,8);
        ok(g.hold(1400,300),"Hold threshold initiates mining");
        inputs.set(-3,-1);
        ok(inputs.held(-1),"Mine input really owns left mouse DOWN");
        ok(e.events.equals(Arrays.asList("mouse0:true")),"One left mouse down");
        g.move(111,107);
        ok(g.moved,"Camera look drift exceeds old swipe slop");
        ok(MiningDragPolicy.keepAttackWhileLooking(g.mining,inputs.held(-1)),"Held mine survives move");
        if(!MiningDragPolicy.keepAttackWhileLooking(g.mining,inputs.held(-1)))inputs.set(-3);
        ok(inputs.held(-1),"Dragging still owns mining");
        ok(e.events.equals(Arrays.asList("mouse0:true")),"No mouse-up on first MOVE");
        g.move(150,150);
        if(!MiningDragPolicy.keepAttackWhileLooking(g.mining,inputs.held(-1)))inputs.set(-3);
        ok(inputs.held(-1),"Repeated MOVE still mines");
        ok(e.events.equals(Arrays.asList("mouse0:true")),"No alternating mouse releases while dragging");
        inputs.set(-3);
        ok(!inputs.held(-1),"Finger-up releases mine");
        ok(e.events.equals(Arrays.asList("mouse0:true","mouse0:false")),"Exactly one DOWN and one UP");
        g.begin(0,0,2000,8);
        g.move(50,50);
        ok(!MiningDragPolicy.keepAttackWhileLooking(g.mining,inputs.held(-1)),"Unheld look swipe not converted to mine");
        ok(!g.hold(2400,300),"Swipe cannot become long-hold mining");
        inputs.set(-3,-2);
        ok(!MiningDragPolicy.keepAttackWhileLooking(true,inputs.held(-1)),"Use-item is not mistaken for mining");
        if(!MiningDragPolicy.keepAttackWhileLooking(true,inputs.held(-1)))inputs.set(-3);
        ok(!inputs.held(-2),"Use-item hold safely cancels on drag");
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Need patched ClassicOverlay.java");
        checkIcons();checkGesture();
        String overlay=new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8);
        int start=overlay.indexOf("if(route.kind==WORLD&&pointer==worldPointer&&playing())");
        int end=overlay.indexOf("}else if(directional(route.button))",start);
        ok(start>0&&end>start,"Actual world gesture branch located");
        String move=overlay.substring(start,end);
        int branch=move.indexOf("if (!MiningDragPolicy.keepAttackWhileLooking(gesture.mining,inputs.held(-1))) {");
        ok(branch>0,"Live MOVE branch uses held-mouse ownership condition");
        ok(move.indexOf("inputs.set(-3)")>branch,"Mouse release occurs only inside conditional");
        ok(move.indexOf("target(0,0,false)")>branch,"Off-centre ray clear occurs only inside conditional");
        ok(move.indexOf("pendingHold=-1")<branch,"Late classification invalidated before conditional");
        ok(move.contains("CallbackBridge.mouseX+=dx;") && move.contains("CallbackBridge.sendCursorPos"),"Look movement still works while mining");
        ok(overlay.contains("inputs.set(-3);worldPointer=-1;"),"Finger-up ends held mining");
        ok(overlay.contains("JoggerTouchBridge.forceAttackRelease()"),"Native finger-up failsafe retained");
        ok(overlay.contains("if(!focused&&!stopped)release();"),"Focus loss cancels mining safely");
        ok(overlay.contains("PixelDirectionIcons.UP_LEFT")&&overlay.contains("PixelDirectionIcons.UP_RIGHT"),"Both diagonal masks are used");
        ok(!overlay.contains("canvas.rotate(90)")&&!overlay.contains("canvas.rotate(-90)"),"No canvas rotations of cardinal icon shadow");
        ok(overlay.contains("float offset=pass==0?pixel:0;"),"Shadow remains down and right in screen axes");
        System.out.println("MiningDragAndArrowTest: "+checks+" checks passed");
    }
}
