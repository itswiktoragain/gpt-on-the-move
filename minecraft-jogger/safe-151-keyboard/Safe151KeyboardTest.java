import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import net.kdt.pojavlaunch.jogger.classic.ClassicTopBarLayout;

/** Verify the 1.5.1 controls are untouched and keyboard HUD button is visible. */
public final class Safe151KeyboardTest {
    private static int checks;
    private static void ok(boolean condition,String what){if(!condition)throw new AssertionError(what);checks++;}
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Pass overlay then MinecraftKeyboard.java");
        String overlay=Files.readString(Path.of(args[0]),StandardCharsets.UTF_8);
        String keyboard=Files.readString(Path.of(args[1]),StandardCharsets.UTF_8);
        ok(overlay.contains("add(KEYBOARD,ClassicTopBarLayout.keyboardLeft("),"Keyboard button visible in 1.5.1 layout");
        ok(!overlay.contains("controlsTop"),"No later 1.5.2 HUD controls layout");
        ok(overlay.contains("mode.equals(\"CHAT\")"),"Keyboard available in chat");
        ok(overlay.contains("b.id==BACK||b.id==SEND||b.id==KEYBOARD"),"Keyboard allowed in chat view");
        ok(overlay.contains("b.id==CHAT||b.id==PAUSE||b.id==MENU||b.id==KEYBOARD"),"Keyboard allowed during gameplay");
        ok(overlay.contains("return b.id==BACK||b.id==MENU||b.id==KEYBOARD"),"Keyboard allowed in menus");
        ok(overlay.contains("MinecraftKeyboard.toggleManual()"),"HUD opens embedded Minecraft keyboard");
        ok(!overlay.contains("TouchRoutingPolicy"),"No 1.6.6 input patch");
        ok(!overlay.contains("WorldGesturePolicy"),"No 1.6.7 input patch");
        ok(!overlay.contains("CameraLookPolicy"),"No 1.6.4 camera patch");
        ok(!overlay.contains("MiningLookPolicy"),"No 1.6.2 mining patch");
        ok(!overlay.contains("PixelDirectionIcons"),"No 1.6.2 icon patch");
        ok(!overlay.contains("lookPointer"),"No 1.6.4 multitouch path injected");
        ok(overlay.contains("gesture.begin(x,y,event.getEventTime(),dp(8))"),"Safe 1.5.1 gesture slop is preserved");
        ok(overlay.contains("CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY)"),"Safe native camera path preserved");
        ok(overlay.contains("handler.removeCallbacks(hold);pendingHold=-1;inputs.set(-3);target(0,0,false);"),
           "Original 1.5.1 gesture release path remains unchanged");
        ok(keyboard.contains("public static void toggleManual()"),"Embedded keyboard can be toggled");
        ok(keyboard.contains("k.manualOverride=false;k.manualUntil=0;k.state.hide();k.apply()"),"Second tap hides keyboard");
        ok(keyboard.contains("manualUntil=Long.MAX_VALUE"),"Manual keyboard remains visible during normal focus updates");
        ok(keyboard.contains("if(!mode.equals(previous))k.manualOverride=false"),"Keyboard override clears on genuine screen changes");
        ok(ClassicTopBarLayout.keyboardLeft(1000,10,53,6,false,false)==878,"Menu position without overlapping");
        ok(ClassicTopBarLayout.keyboardLeft(1000,10,53,6,true,false)==937,"Gameplay button at far right");
        ok(ClassicTopBarLayout.keyboardLeft(1000,10,53,6,false,true)==878,"Chat button adjacent to Send");
        System.out.println("Safe151KeyboardTest: "+checks+" checks passed; safe gameplay untouched");
    }
}
