import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.kdt.pojavlaunch.jogger.classic.ClassicLogic;

/** Input release boundary tests, no Android dependency or camera modification. */
public final class MiningRelease140Test {
    private static int checks;
    private static void ok(boolean x,String reason){if(!x)throw new AssertionError(reason);checks++;}
    private static final class Output implements ClassicLogic.Output {
        boolean attack,use;
        int leftPress,leftRelease,rightPress,rightRelease;
        public void key(int key,boolean down){}
        public void mouse(int button,boolean down){
            if(button==0){attack=down;if(down)leftPress++;else leftRelease++;}
            if(button==1){use=down;if(down)rightPress++;else rightRelease++;}
        }
    }
    /** Simulates only the stale-ACK gate implemented verbatim in the bridge. */
    private static final class AckGate {
        boolean left;
        int downEdges,upEdges;
        void deliver(boolean pressed){left=pressed;if(pressed)downEdges++;else upEdges++;}
        void ack(boolean incomingDown,boolean desiredDown){
            if(incomingDown && !desiredDown){deliver(true);deliver(false);}
            else if(!incomingDown && desiredDown){/* Old up cannot interrupt new mining */ }
            else deliver(incomingDown);
        }
        void immediateRelease(boolean desiredDown){if(!desiredDown)deliver(false);}
    }
    static void sequence(int iteration){
        Output output=new Output();
        ClassicLogic.Inputs inputs=new ClassicLogic.Inputs(output);
        int worldOwner=-3,buttonOwner=101+(iteration%17);
        // A touch-and-hold press owns left click.
        inputs.set(worldOwner,-1);
        ok(output.attack,"Long press mines: "+iteration);
        // A second mining button may own the same click at once.
        if(iteration%3==0){
            inputs.set(buttonOwner,-1);
            inputs.set(worldOwner);
            ok(output.attack,"Lifting first owner keeps mine held: "+iteration);
            inputs.set(buttonOwner);
        }else inputs.set(worldOwner);
        ok(!output.attack,"Final finger UP releases mining: "+iteration);
        ok(output.leftPress==1,"No ghost extra left mouse-down: "+iteration);
        ok(output.leftRelease==1,"One left mouse-up when last owner releases: "+iteration);
        // Right click and the WASD/jump key owners cannot be released by
        // a mining-only fallback.
        inputs.set(211,-2);
        inputs.set(212,87,32);
        ok(output.use,"Separate interaction still held: "+iteration);
        inputs.set(worldOwner);
        ok(output.use,"Mining cleanup never releases right click: "+iteration);
        ok(inputs.held(87)&&inputs.held(32),"Mining cleanup never cancels movement and jump: "+iteration);
        inputs.set(211);
        inputs.set(212);
        ok(!output.use,"Right click releases normally: "+iteration);
        // Java agent ACK can arrive OUT OF ORDER from a previous touch.
        AckGate gate=new AckGate();
        gate.ack(true,true);
        ok(gate.left,"Valid down is delivered: "+iteration);
        gate.immediateRelease(false);
        ok(!gate.left,"Native UP is immediate: "+iteration);
        int before=gate.downEdges;
        gate.ack(true,false);
        ok(!gate.left,"Late down can never relatch mining: "+iteration);
        ok(gate.downEdges==before+1,"Late short-click edge is preserved: "+iteration);
        gate.ack(false,false);
        ok(!gate.left,"Old release is idempotent: "+iteration);
        gate.ack(true,true);
        gate.ack(false,true);
        ok(gate.left,"Stale up cannot cancel a NEW hold: "+iteration);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("overlay, bridge, baseline overlay, baseline bridge");
        String overlay=new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8);
        String bridge=new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8);
        String original=new String(Files.readAllBytes(Paths.get(args[2])),StandardCharsets.UTF_8);
        String oldBridge=new String(Files.readAllBytes(Paths.get(args[3])),StandardCharsets.UTF_8);
        for(int i=0;i<2500;i++)sequence(i);
        ok(overlay.contains("if(action==MotionEvent.ACTION_OUTSIDE){release();return true;}"),"Lost outside touch releases");
        ok(overlay.contains("if(action==MotionEvent.ACTION_DOWN && (worldPointer>=0||routes.size()>0))release();"),"New touch clears orphaned mining");
        ok(overlay.contains("if(action==MotionEvent.ACTION_UP && worldPointer>=0 && id!=worldPointer)"),"Mismatched final UP clears mining");
        ok(overlay.contains("if(route==null && (id==worldPointer ||"),"Lost final touch route releases");
        ok(overlay.contains("if(route!=null && route.kind==MINE && !inputs.held(-1))"),"Explicit Mine button UP releases only when unowned");
        ok(overlay.contains("if(!inputs.held(-1))JoggerTouchBridge.releaseAttackIfUnheld();"),"World-finger UP releases left mouse");
        ok(overlay.contains("inputs.clear();\n        JoggerTouchBridge.releaseAttackIfUnheld();"),"Cancel releases attack immediately");
        ok(overlay.contains("if(action==MotionEvent.ACTION_CANCEL){release();return true;}"),"Original Android cancel preserved");
        ok(bridge.contains("public static void releaseAttackIfUnheld()"),"Reliable native release implemented");
        ok(bridge.contains("if(HELD_MOUSE.containsKey(0))return;"),"Other active mining owners protected");
        ok(bridge.contains("deliverMouse(0,0,false);"),"Native mouse up is immediate");
        ok(bridge.contains("if(state==1 && !wanted)"),"Outdated left button DOWN guarded");
        ok(bridge.contains("else if(state==0 && wanted)"),"Outdated left button UP guarded");
        ok(bridge.contains("deliverMouse(0,mods,true);\n                                    deliverMouse(0,0,false);"),"Late click never latches");
        // GUARDED 1.4 CAMERA: byte-for-byte original method bodies.
        String[] boundaries={
            "    public static void report(String state, boolean capability) {",
            "    public static void physicalKey(",
            "    @Override public void onGrabState(boolean grabbed) {",
            "    @Override public void onOptionChanged()",
            "    private boolean playing() {",
            "    private float dp(",
            "        if(action==MotionEvent.ACTION_MOVE) {",
            "        if(action==MotionEvent.ACTION_CANCEL)",
            "    private boolean targetEnabled()",
            "    public void release()"
        };
        for(int i=0;i<boundaries.length;i+=2){
            String start=boundaries[i],end=boundaries[i+1];
            ok(block(original,start,end).equals(block(overlay,start,end)),"Original camera/grab/move block untouched: "+start);
        }
        ok(overlay.contains("CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY)"),"Native camera movement retained");
        ok(original.split("CallbackBridge.sendCursorPos\\(", -1).length==overlay.split("CallbackBridge.sendCursorPos\\(", -1).length,"No additional cursor moves");
        ok(bridge.split("CallbackBridge.sendCursorPos\\(", -1).length==oldBridge.split("CallbackBridge.sendCursorPos\\(", -1).length,"Bridge has no new camera writes");
        ok(!overlay.contains("CameraLookPolicy")&&!overlay.contains("TouchRoutingPolicy")&&!overlay.contains("WorldGesturePolicy"),"No 1.6+ camera patch");
        System.out.println("MiningRelease140Test: "+checks+" checks passed with original 1.4 camera logic intact");
    }
    private static String block(String code,String start,String end){
        int i=code.indexOf(start);
        if(i<0)throw new AssertionError("Missing source block "+start);
        int j=code.indexOf(end,i);
        if(j<0)throw new AssertionError("Missing endpoint "+end);
        return code.substring(i,j);
    }
}
