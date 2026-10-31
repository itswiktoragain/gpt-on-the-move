import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.kdt.pojavlaunch.jogger.classic.ClassicLogic;
import net.kdt.pojavlaunch.jogger.classic.TouchRoutingPolicy;

/** Tests state-guard, tap/mine/use semantics, and real ClassicOverlay source routing. */
public final class GameplayInput165Test {
    private static int checks;
    private static void ok(boolean actual,String name){if(!actual)throw new AssertionError(name);checks++;}
    private static void equals(int actual,int expected,String name){ok(actual==expected,name+": "+actual+" expected "+expected);}
    private static void equals(String actual,String expected,String name){ok(expected.equals(actual),name+": "+actual+" expected "+expected);}

    private static void checkActions() {
        equals(ClassicLogic.TouchAction.tap(0),-1,"Single tap on entity ATTACKS with left click");
        equals(ClassicLogic.TouchAction.tap(1),-2,"Single tap on block places/uses with right click");
        equals(ClassicLogic.TouchAction.tap(2),0,"Single tap on air must not use an item");
        equals(ClassicLogic.TouchAction.hold(0),-2,"Hold on entity interacts rather than attacking");
        equals(ClassicLogic.TouchAction.hold(1),-1,"Hold on block maintains mining");
        equals(ClassicLogic.TouchAction.hold(2),-2,"Hold on air uses item/eats/charges shield");
        equals(ClassicLogic.TouchAction.hold(TouchRoutingPolicy.holdFallbackHit(true)),-2,"Known-ray timeout defaults to held item use");
        equals(ClassicLogic.TouchAction.hold(TouchRoutingPolicy.holdFallbackHit(false)),-1,"Unknown ray remains mining fallback");
    }
    private static void checkNativeStatus() {
        equals(TouchRoutingPolicy.effectiveMode(true,"SCREEN","PLAY"),"PLAY","Stale agent SCREEN cannot steal native PLAY");
        equals(TouchRoutingPolicy.effectiveMode(true,"CHAT","PLAY"),"PLAY","Stale agent CHAT cannot steal native look");
        equals(TouchRoutingPolicy.effectiveMode(true,"INVENTORY","PLAY"),"PLAY","Stale agent INVENTORY cannot freeze camera");
        equals(TouchRoutingPolicy.effectiveMode(false,"CHAT","PLAY"),"CHAT","Actual game chat still opens when native ungrabbed");
        equals(TouchRoutingPolicy.effectiveMode(false,"INVENTORY","PLAY"),"INVENTORY","Actual inventory still opens when native ungrabbed");
        equals(TouchRoutingPolicy.effectiveMode(false,"PLAY","SCREEN"),"SCREEN","Late PLAY ignored after native ungrab");
        equals(TouchRoutingPolicy.effectiveMode(false,"PLAY","CHAT"),"CHAT","Late PLAY doesn't break the chat keyboard");
        ok(!TouchRoutingPolicy.acceptGrabCallback(false,true),"Delayed release callback rejected after re-grab");
        ok(!TouchRoutingPolicy.acceptGrabCallback(true,false),"Delayed grab callback rejected after ungrab");
        ok(TouchRoutingPolicy.acceptGrabCallback(true,true),"Real grab callback accepted");
        ok(TouchRoutingPolicy.acceptGrabCallback(false,false),"Real ungrab callback accepted");
        ok(!TouchRoutingPolicy.isNewGrabState(true,"PLAY"),"Duplicate native grab must not cancel swipe");
        ok(!TouchRoutingPolicy.isNewGrabState(false,"CHAT"),"Duplicate ungrab must not close chat");
        ok(TouchRoutingPolicy.isNewGrabState(true,"SCREEN"),"Native regrab transitions to PLAY");
        ok(TouchRoutingPolicy.isNewGrabState(false,"PLAY"),"Native ungrab transitions to screen");
        ok(TouchRoutingPolicy.cameraSlopDp()>0&&TouchRoutingPolicy.cameraSlopDp()<4,"Camera starts within a small intentional swipe, never old 8dp");
    }
    private static void checkSource(String source) {
        ok(source.contains("private boolean playing() { return !stopped && CallbackBridge.isGrabbing(); }"),"Native grabbed state controls PLAY");
        ok(source.contains("TouchRoutingPolicy.effectiveMode(CallbackBridge.isGrabbing(),state,overlay.mode)"),"Stale status cannot switch scene to SCREEN");
        ok(source.contains("TouchRoutingPolicy.acceptGrabCallback(grabbed,CallbackBridge.isGrabbing())"),"Stale native grab callbacks ignored");
        ok(source.contains("TouchRoutingPolicy.isNewGrabState(grabbed,mode)"),"Duplicate callbacks do not cancel an active gesture");
        ok(source.contains("gesture.begin(x,y,event.getEventTime(),dp(TouchRoutingPolicy.cameraSlopDp()))"),"New camera swipe threshold integrated");
        ok(source.contains("x-(cameraTurning?gesture.lastX:gesture.startX)"),"First camera delta includes movement from swipe start");
        ok(source.contains("cameraTurning=true;"),"Subsequent camera deltas incremental");
        ok(source.contains("routes.put(id,new Route(LOOK,null));lookPointer=id;lookX=x;lookY=y;"),"Independent look finger still routed");
        ok(source.contains("if(dx!=0||dy!=0)lookMoved=true;"),"Second finger movement tracked to prevent accidental place");
        ok(source.contains("!playing()||lookMoved"),"Looking with second finger never accidentally places block");
        String nativeCallback="CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY)";
        ok(source.split(java.util.regex.Pattern.quote(nativeCallback),-1).length==3,"Exactly two launcher-side camera output routes");
        ok(!source.contains("aimLegacy(")&&!source.contains("TouchRay.project("),"No agent camera warp");
        ok(source.contains("pendingProbe=++tapSequence;probeHit=-1;"),"Hit classification prefetched at touch DOWN");
        ok(source.contains("if(!JoggerTouchBridge.requestTap(pendingProbe))pendingProbe=-1;"),"Prefetch handles unavailable game agent");
        ok(source.contains("if(probeHit>=0)finishTap(request,probeHit);"),"Fast block/mob tap uses prefetched hit immediately");
        ok(source.contains("if(probeHit>=0)finishHold(pendingHold,probeHit);"),"Fast block/air long hold uses prefetched hit immediately");
        ok(source.contains("if(request==overlay.pendingProbe)"),"Agent reply recognized while waiting or after UP");
        ok(source.contains("if(request==overlay.pendingHold)overlay.finishHold(request,hit);"),"Late reply can complete held interaction");
        ok(source.contains("if(!gesture.mining){"),"Pre-hold drag cancels tap, post-hold drag preserves action");
        ok(source.contains("if(!gesture.mining){\n                            pendingHold=-1;pendingProbe=-1;"),"Only a pre-hold look cancels delayed hit classification");
        ok(!source.contains("inputs.set(-3,-1);"),"No speculative mouse-down that punches mobs or stops use in air");
        String finishHold=source.substring(source.indexOf("private void finishHold("),source.indexOf("private void finishTap("));
        ok(!finishHold.contains("gesture.moved"),"Camera motion after hold doesn't discard valid hit reply");
        ok(finishHold.contains("inputs.set(-3,ClassicLogic.TouchAction.hold(hit))"),"Held action uses actual hit category");
        ok(source.contains("int code=ClassicLogic.TouchAction.tap(button);"),"Taps distinguish mob, block, and MISS");
        ok(source.contains("if(code!=0)pulse(code);"),"Taps on empty air do not issue ghost use actions");
        ok(source.contains("},480);"),"Lost game-agent response cannot retain touch ray forever");
        ok(source.contains("pendingTap=-1;pendingHold=-1;pendingProbe=-1;probeHit=-1;"),"Release cancels every pending hit request");
        ok(source.contains("JoggerTouchBridge.forceAttackRelease()"),"Native finger-up attack release remains");
        ok(source.contains("PixelIconLayout.place(button.icon,r.left,r.top,r.width(),r.height())"),"Centered icon layout retained");
        ok(source.contains("PixelDirectionIcons.UP_LEFT")&&source.contains("PixelDirectionIcons.UP_RIGHT"),"Diagonal art retained");
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Provide assembled ClassicOverlay.java");
        checkActions();checkNativeStatus();
        checkSource(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));
        System.out.println("GameplayInput165Test: "+checks+" checks passed");
    }
}
