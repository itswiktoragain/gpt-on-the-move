import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.kdt.pojavlaunch.jogger.classic.*;

/** Regression: separate camera movement from tap/hold intent for 1000 gestures. */
public final class InteractionRecovery167Test {
    static int checks;
    static void ok(boolean yes,String message){if(!yes)throw new AssertionError(message);checks++;}
    static void eq(int actual,int expected,String message){
        ok(actual==expected,message+": expected "+expected+", got "+actual);
    }
    static class Output implements ClassicLogic.Output {
        int down,up;
        @Override public void key(int key,boolean pressed){}
        @Override public void mouse(int button,boolean pressed){
            if(pressed)down++; else up++;
        }
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("patched overlay path required");
        String src=new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8);
        ok(src.contains("gesture.begin(x,y,event.getEventTime(),dp(WorldGesturePolicy.actionSlopDp()))"),"Touch uses wide action threshold");
        ok(src.contains("WorldGesturePolicy.rotate(cameraTurning,gesture.mining,"),"Camera has own independent low threshold");
        ok(src.contains("if(gesture.moved && !gesture.mining){"),"Only true swipes cancel pending actions");
        ok(src.contains("WorldGesturePolicy.correctHit(hit,raySupported,targetEnabled())"),"Long hold uses vanilla fallback if agent unavailable");
        ok(src.contains("WorldGesturePolicy.correctHit(button,raySupported,targetEnabled())"),"Tap uses vanilla fallback if agent unavailable");
        ok(src.contains("TouchRoutingPolicy.canRouteWorld(CallbackBridge.isGrabbing(),mode)"),"Recovery from temporary pointer ungrab retained");
        ok(src.split(java.util.regex.Pattern.quote("CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY)"),-1).length==3,
            "Both camera routes are launcher native and separate from game agent");
        ok(WorldGesturePolicy.actionSlopDp()>=14,"Normal finger jitter must not cancel tap");
        ok(WorldGesturePolicy.cameraSlopDp()<4,"Camera should still start promptly");
        ok(WorldGesturePolicy.actionSlopDp()>=4*WorldGesturePolicy.cameraSlopDp(),"Distinct thresholds");

        for(int i=0;i<1000;i++){
            ClassicLogic.Gesture g=new ClassicLogic.Gesture();
            g.begin(100,100,2000,WorldGesturePolicy.actionSlopDp());
            float jitter=3+(i%13);
            g.move(100+jitter,100);
            ok(!g.moved,"3-15dp finger wobble must not invalidate an action: "+i);
            ok(WorldGesturePolicy.rotate(false,false,jitter,0,WorldGesturePolicy.cameraSlopDp()),
                "Camera still rotates for that same movement: "+i);
            if(i%2==0){
                ok(g.end(2100,280,false),"Tap survives jitter: "+i);
                int hit=i%4==0?1:0;
                eq(ClassicLogic.TouchAction.tap(hit),hit==0?-1:-2,"Tap selects mob attack or block place: "+i);
            }else {
                ok(g.hold(2400,280),"Hold starts after jitter: "+i);
                int hit=i%4==1?1:2;
                eq(ClassicLogic.TouchAction.hold(hit),hit==1?-1:-2,"Hold mines block or uses item in air: "+i);
                ok(!g.end(2500,280,false),"Hold does not accidentally place: "+i);
            }
        }
        for(int i=0;i<300;i++){
            Output o=new Output();
            ClassicLogic.Inputs inputs=new ClassicLogic.Inputs(o);
            int code=i%2==0?-1:-2;
            inputs.set(-3,code);
            ok(inputs.held(code),"Left/right mouse DOWN sent: "+i);
            inputs.set(-3);
            ok(!inputs.held(code),"Finger UP releases mouse: "+i);
            eq(o.down,1,"One button DOWN");
            eq(o.up,1,"One button UP");
        }
        for(int hit=0;hit<3;hit++)
            eq(WorldGesturePolicy.correctHit(hit,true,true),hit,"Valid entity/block/miss preserved");
        eq(WorldGesturePolicy.correctHit(2,false,true),1,"Disconnected agent defaults to crosshair block use");
        eq(WorldGesturePolicy.correctHit(2,true,false),1,"Disabled touch targeting defaults to crosshair block use");
        eq(TouchRoutingPolicy.holdFallbackHit(true),1,"Classify timeout prioritizes mining");
        eq(TouchRoutingPolicy.holdFallbackHit(false),1,"Disconnected classify prioritizes mining");
        ClassicLogic.Gesture swipe=new ClassicLogic.Gesture();
        swipe.begin(10,10,0,WorldGesturePolicy.actionSlopDp());
        swipe.move(130,130);
        ok(swipe.moved,"Real long swipe detected");
        ok(!swipe.hold(450,280),"Long swipe does not begin mine");
        ok(!swipe.end(480,280,false),"Long swipe does not place");
        System.out.println("InteractionRecovery167Test: "+checks+" checks passed");
    }
}
