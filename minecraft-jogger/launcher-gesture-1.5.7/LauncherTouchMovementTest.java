import net.kdt.pojavlaunch.jogger.classic.ClassicLogic;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Regression for the earlier Minecraft Jogger launcher gesture policy. */
public final class LauncherTouchMovementTest {
    private static int checks;
    private static void ok(boolean good, String label) {
        if (!good) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=1) throw new IllegalArgumentException("Pass ClassicOverlay.java");
        String src=new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8);
        int begin=src.indexOf("if(action==MotionEvent.ACTION_MOVE)");
        int end=src.indexOf("if(action==MotionEvent.ACTION_CANCEL)",begin);
        ok(begin>=0&&end>begin,"MOVE routing present");
        String move=src.substring(begin,end);
        ok(move.contains("gesture.move(x,y);"),"finger movement processed while mining");
        ok(!move.contains("if(!gesture.mining)gesture.move"),"1.5.5 movement lock removed");
        ok(move.contains("if(gesture.moved) {"),"restore older Jogger drag-to-look");
        ok(move.contains("pendingHold=-1;inputs.set(-3);target(0,0,false);"),"drag releases mining");
        ok(move.contains("else if(gesture.mining) {"),"fine-grained hold retarget");
        ok(move.contains("target(x,y,true);"),"mining ray follows short slides");
        ok(move.contains("CallbackBridge.mouseX+=dx;CallbackBridge.mouseY+=dy;"),"real movement causes smooth camera delta");
        ok(!src.contains("aimLegacy("),"no synthetic camera warp");
        ok(src.contains("gesture.begin(x,y,event.getEventTime(),dp(8));target(x,y,true);"),"tap ray targets finger");
        ok(src.contains("inputs.set(-3);worldPointer=-1;"),"release ends mining");
        ClassicLogic.Gesture g=new ClassicLogic.Gesture();
        g.begin(200,150,100,8);
        ok(g.active&&!g.moved&&!g.mining,"touch starts clean");
        ok(!g.hold(250,200),"no early hold");
        ok(g.hold(300,200),"stationary hold mines");
        g.move(205,153);
        ok(g.mining&&!g.moved,"tiny correction keeps mining");
        g.move(220,175);
        ok(g.moved,"drag can move viewpoint after hold");
        ok(!g.end(400,200,false),"drag does not place accidentally");
        g.begin(101,102,1000,8);
        ok(g.end(1100,200,false),"short tap interacts");
        g.begin(101,102,1000,8);
        g.move(140,160);
        ok(!g.hold(1300,200),"look drag never starts mining");
        ok(ClassicLogic.TouchAction.tap(0)==-1,"entity tap attacks");
        ok(ClassicLogic.TouchAction.tap(1)==-2,"block tap places/uses");
        ok(ClassicLogic.TouchAction.hold(1)==-1,"block hold mines");
        ok(ClassicLogic.TouchAction.hold(2)==-2,"air hold uses/eats");
        System.out.println("LauncherTouchMovementTest: "+checks+" checks passed");
    }
}
