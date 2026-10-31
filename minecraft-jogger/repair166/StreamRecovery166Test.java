import java.nio.file.*;
import net.kdt.pojavlaunch.jogger.classic.TouchRoutingPolicy;
/** Regression: 1000 tap-place-drag cycles, intermittent native-grab flicker. */
public final class StreamRecovery166Test {
    private static int checks;
    private static void ok(boolean test,String message){if(!test)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("patched overlay path required");
        ok("PLAY".equals(TouchRoutingPolicy.effectiveMode(false,"PLAY","SCREEN")),"World report restores touch after false ungrab");
        ok("PLAY".equals(TouchRoutingPolicy.effectiveMode(false,"PLAY","PLAY")),"Repeated PLAY report preserves world");
        ok("CHAT".equals(TouchRoutingPolicy.effectiveMode(false,"PLAY","CHAT")),"Stale PLAY cannot steal chat");
        ok("INVENTORY".equals(TouchRoutingPolicy.effectiveMode(false,"PLAY","INVENTORY")),"Stale PLAY cannot steal inventory");
        ok("SCREEN".equals(TouchRoutingPolicy.effectiveMode(false,"SCREEN","PLAY")),"True menu transition still allowed");
        ok("CHAT".equals(TouchRoutingPolicy.effectiveMode(false,"CHAT","PLAY")),"Chat transition still allowed");
        ok("PLAY".equals(TouchRoutingPolicy.effectiveMode(true,"SCREEN","PLAY")),"Native grab authoritative");
        ok(TouchRoutingPolicy.canRouteWorld(false,"PLAY"),"Gameplay receives DOWN despite momentary lost grab");
        ok(TouchRoutingPolicy.canRouteWorld(true,"SCREEN"),"Native grab recovers screen state");
        ok(!TouchRoutingPolicy.canRouteWorld(false,"SCREEN"),"Actual screen touch passes to GUI");
        ok(!TouchRoutingPolicy.canRouteWorld(false,"CHAT"),"Chat receives GUI touches");
        ok(TouchRoutingPolicy.preserveWorldOnUngrab(false,"PLAY","PLAY"),"Transient ungrab does not drop pointer");
        ok(!TouchRoutingPolicy.preserveWorldOnUngrab(false,"PLAY","SCREEN"),"Real GUI state releases pointer");
        ok(!TouchRoutingPolicy.preserveWorldOnUngrab(true,"PLAY","PLAY"),"Real regrab is processed");
        ok(!TouchRoutingPolicy.preserveWorldOnUngrab(false,"CHAT","PLAY"),"Intentional chat is not blocked");
        String mode="PLAY",reported="PLAY";
        for(int i=0;i<1000;i++){
            boolean grabbed=i%3!=0;
            if(!grabbed&&TouchRoutingPolicy.preserveWorldOnUngrab(false,mode,reported))
                ok("PLAY".equals(mode),"Tap/place cycle keeps PLAY on false ungrab "+i);
            mode=TouchRoutingPolicy.effectiveMode(grabbed,reported,mode);
            ok(TouchRoutingPolicy.canRouteWorld(grabbed,mode),"DOWN accepted "+i);
            for(int m=0;m<5;m++)ok(TouchRoutingPolicy.canRouteWorld(grabbed,mode),"MOVE stays routed "+i+":"+m);
            ok(TouchRoutingPolicy.canRouteWorld(grabbed,mode),"UP routed "+i);
        }
        String source=Files.readString(Path.of(args[0]));
        ok(source.contains("TouchRoutingPolicy.canRouteWorld(CallbackBridge.isGrabbing(),mode)"),"Android touch router uses native + confirmed world state");
        ok(source.contains("TouchRoutingPolicy.preserveWorldOnUngrab(grabbed,mode,lastGameMode)"),"Spurious ungrab cannot cancel active touch");
        ok(source.contains("overlay.lastGameMode=state;"),"Latest game mode tracked");
        ok(source.contains("if(action==MotionEvent.ACTION_DOWN && !playing() && hit(event.getX(),event.getY())==null)return false;"),"Gameplay DOWN kept in overlay");
        ok(source.contains("if(route.kind==WORLD&&pointer==worldPointer&&playing())"),"Primary drag handled");
        ok(source.contains("route.kind==LOOK&&pointer==lookPointer&&playing()"),"Second-finger drag handled");
        ok(source.split(java.util.regex.Pattern.quote("CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY)"),-1).length==3,"Only two launcher-native camera routes");
        ok(!source.contains("TouchRay.project("),"No agent camera rotation");
        System.out.println("StreamRecovery166Test: "+checks+" checks passed");
    }
}
