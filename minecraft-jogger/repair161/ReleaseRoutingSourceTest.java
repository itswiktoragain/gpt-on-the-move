import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Protects both Android's gesture edge cases and the socket-to-native release boundary. */
public final class ReleaseRoutingSourceTest {
    private static int checks;
    private static void ok(boolean condition, String msg) {
        if(!condition) throw new AssertionError(msg);
        checks++;
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Need overlay and bridge source");
        String overlay=new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8);
        String bridge=new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8);
        ok(overlay.contains("ACTION_CANCEL){release();return true;}"),"Touch cancel releases");
        ok(overlay.contains("ACTION_OUTSIDE){release();return true;}"),"Outside event releases");
        ok(overlay.contains("ACTION_DOWN && worldPointer>=0)release();"),"New touch clears orphaned press");
        ok(overlay.contains("getPointerId(i)==worldPointer"),"Track original finger across multi-touch");
        ok(overlay.contains("if(!worldFingerPresent){release();return true;}"),"Lost pointer releases");
        ok(overlay.contains("id!=worldPointer"),"Unexpected final pointer route triggers release");
        ok(overlay.contains("if(route==null&&id==worldPointer){release();return true;}"),"Lost route on finger-up triggers release");
        ok(overlay.contains("if(!focused&&!stopped)release();"),"Background/focus loss releases");
        ok(overlay.contains("if(!inputs.held(-1))JoggerTouchBridge.forceAttackRelease();"),
                "Finger release forces native UP unless other control owns attack");
        ok(overlay.contains("inputs.clear();JoggerTouchBridge.forceAttackRelease();"),
                "All cancellation/release paths force native UP");
        ok(overlay.contains("pendingHold=-1;inputs.set(-3);worldPointer=-1;"),
                "Release also invalidates pending game-thread mining classification");
        ok(bridge.contains("HELD_MOUSE.remove(0);"),"Force-release removes desired attack hold");
        ok(bridge.contains("current.queue.offer(\"M \"+epoch+\" 0 0 0\")"),
                "Game JVM also receives reliable release");
        ok(bridge.contains("deliverMouse(0,0,false);"),"Native mouse-up sent immediately");
        ok(bridge.contains("MouseAckPolicy.shouldForward(button,state==1,wanted)"),
                "Late ACK cannot re-press mining");
        ok(overlay.contains("PixelDiagonalIcons.UP_LEFT") && overlay.contains("PixelDiagonalIcons.UP_RIGHT"),
                "Diagonal arrows have independent square-cell rasters");
        ok(!overlay.contains("canvas.rotate(-45)")&&!overlay.contains("canvas.rotate(45)"),
                "No icon is rotated by 45 degrees");
        ok(overlay.contains("float offset=pass==0?pixel:0;"),
                "Pixel-art shadow aligned to full square pixel grid");
        ok(overlay.contains("MinecraftKeyboard.toggleManual()"),
                "Manual keyboard button remains in 1.6.1");
        ok(!overlay.contains("aimLegacy("),"Camera-teleport workaround remains removed");
        ok(overlay.split(java.util.regex.Pattern.quote("CallbackBridge.mouseX+="), -1).length-1==1,
                "Camera only moves via intentional finger drag");
        System.out.println("ReleaseRoutingSourceTest: "+checks+" checks passed");
    }
}
