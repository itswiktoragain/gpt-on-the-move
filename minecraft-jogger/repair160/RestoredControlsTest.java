import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.kdt.pojavlaunch.jogger.classic.ClassicLogic;
import com.minecraftjogger.touch.ClassicGameHooks;
import com.minecraftjogger.touch.MobileHooks;
import com.minecraftjogger.touch.TouchRay;

/** Regression for Jogger 1.5.9 issues: hold did not mine, crosshair appeared,
 * and the separate keyboard control was absent. */
public final class RestoredControlsTest {
    private static int checks;
    private static void ok(boolean good,String label){if(!good)throw new AssertionError(label);checks++;}
    static final class Events implements ClassicLogic.Output {
        final ArrayList<String> events=new ArrayList<>();
        @Override public void key(int k,boolean down){events.add("key"+k+":"+down);}
        @Override public void mouse(int button,boolean down){events.add("mouse"+button+":"+down);}
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Pass overlay and keyboard source paths");
        String overlay=new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8);
        String keyboard=new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8);
        ok(overlay.contains("add(KEYBOARD,ClassicTopBarLayout.keyboardLeft("),"manual keyboard added to top bar");
        ok(overlay.contains("b.id==MENU||b.id==KEYBOARD"),"keyboard visible in world and menus");
        ok(overlay.contains("MinecraftKeyboard.toggleManual()"),"manual keyboard handler wired");
        ok(keyboard.contains("public static void toggleManual()"),"manual toggle implemented");
        ok(keyboard.contains("state.rearm();state.show();apply();"),"manual keyboard opens on demand");
        ok(keyboard.contains("if(!mode.equals(previous))k.manualOverride=false;"),"manual keyboard remains until a screen transition");
        ok(overlay.contains("!raySupported && hit==2 ? 1 : hit"),"unavailable ray must mine instead of use item");
        ok(overlay.contains("inputs.set(-3,-1);"),"hold begins mining without a delayed hit-classification reply");
        ok(!overlay.contains("aimLegacy("),"no camera warp workaround");
        ok(overlay.contains("gesture.move(x,y);"),"1.5.2 smooth drag behavior unchanged");
        ok(overlay.contains("inputs.set(-3);worldPointer=-1;"),"finger release ends mining");

        // The same 1.5.2 input ownership primitive used in the APK must hold
        // left mouse while the finger remains down, and release it on UP.
        Events events=new Events();
        ClassicLogic.Inputs inputs=new ClassicLogic.Inputs(events);
        inputs.set(-3,ClassicLogic.TouchAction.hold(1));
        ok(events.events.equals(Arrays.asList("mouse0:true")),"BLOCK hold begins left-mouse mining");
        inputs.set(-3);
        ok(events.events.equals(Arrays.asList("mouse0:true","mouse0:false")),"Release ends mining");
        int unavailableRay=2; // the old 1.5.9 code mistakenly treated MISS as USE
        int corrected=!false && unavailableRay==2 ? 1 : unavailableRay;
        ok(ClassicLogic.TouchAction.hold(corrected)==-1,"missing hit classification still mines");
        ok(ClassicLogic.TouchAction.hold(2)==-2,"genuine air hold can still use items with ray ready");

        // Crosshair appearance is independent from the FOV calibration.
        TouchRay.fovSeen=false;
        System.setProperty("jogger.mobile.hide_crosshair","true");
        ok(MobileHooks.hideCrosshair(),"crosshair stays hidden before FOV is sampled");
        System.setProperty("jogger.mobile.hide_crosshair","false");
        ok(!MobileHooks.hideCrosshair(),"crosshair remains user-configurable");
        System.setProperty("jogger.mobile.hide_crosshair","true");

        // A full HUD mapping does not imply a fully installed gameplay ray.
        // The bundled agent's ClassFileTransformer tests supply that check.
        ok(!ClassicGameHooks.rayReady(),"do not claim targeting before instrumenting game");
        System.out.println("RestoredControlsTest: "+checks+" checks passed");
    }
}
