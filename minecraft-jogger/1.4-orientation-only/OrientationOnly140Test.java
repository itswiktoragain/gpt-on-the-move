import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import net.kdt.pojavlaunch.jogger.mobile.RenderSurfacePolicy;

/**
 * Rendering fix only: tests the exact Samsung/Android 15+ policy from 1.5.1.
 * All gameplay sources are checked byte-for-byte by confirm_source.py.
 */
public final class OrientationOnly140Test {
    private static int checks;
    private static void ok(boolean b,String name){if(!b)throw new AssertionError(name);checks++;}
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("expected prefs, MinecraftGLSurface, overlay");
        ok(RenderSurfacePolicy.needsTextureView(36,"Samsung"),"Samsung Android 16 uses TextureView");
        ok(RenderSurfacePolicy.needsTextureView(35,"samsung"),"Samsung Android 15 uses TextureView");
        ok(RenderSurfacePolicy.needsTextureView(36," SAMSUNG "),"Manufacturer matching ignores case/space");
        ok(!RenderSurfacePolicy.needsTextureView(34,"samsung"),"Android 14 retains 1.4 surface");
        ok(!RenderSurfacePolicy.needsTextureView(36,"google"),"Other manufacturers keep 1.4 surface");
        ok(!RenderSurfacePolicy.needsTextureView(36,null),"Null manufacturer safe");
        ok(!RenderSurfacePolicy.needsTextureView(30,"samsung"),"Android 11 not switched");
        ok(RenderSurfacePolicy.migrateOnce(false,true),"First launch corrects affected installed profile");
        ok(!RenderSurfacePolicy.migrateOnce(true,true),"Subsequent launches preserve user selection");
        ok(!RenderSurfacePolicy.migrateOnce(false,false),"Non Samsung profile remains untouched");
        for(int dimension=0;dimension<=4000;dimension+=7){
            for(float scale:new float[]{0.35f,0.50f,0.70f,0.99f,1f}){
                int resized=RenderSurfacePolicy.evenScaled(dimension,scale);
                ok(resized%2==0,"Renderer buffer width must be even");
                ok(dimension==0 ? resized==0 : resized>=2,"Zero handling and positive sizes");
            }
        }
        String prefs=new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8);
        String surface=new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8);
        String overlay=new String(Files.readAllBytes(Paths.get(args[2])),StandardCharsets.UTF_8);
        ok(prefs.contains("jogger_surface_orientation_migrated_v140_only"),"Force one-time migration after 1.6.x downgrade");
        ok(prefs.contains(".putBoolean(\"alternate_surface\", false)"),"Affected Samsung uses TextureView");
        ok(prefs.contains("PREF_USE_ALTERNATE_SURFACE = DEFAULT_PREF.getBoolean(\"alternate_surface\", isDevicePowerful)"),"Original setting retained");
        ok(surface.contains("surface.setDefaultBufferSize(w, h)"),"Initial native buffer is fixed");
        ok(surface.contains("refreshSize(true)"),"Recreated native surface refreshes buffer size");
        ok(surface.contains("RenderSurfacePolicy.evenScaled(width, LauncherPreferences.PREF_SCALE_FACTOR)"),"Resize scales width safely");
        ok(surface.contains("RenderSurfacePolicy.evenScaled(height, LauncherPreferences.PREF_SCALE_FACTOR)"),"Resize scales height safely");
        ok(!overlay.contains("TouchRoutingPolicy"),"No 1.6+ mouse routing");
        ok(!overlay.contains("WorldGesturePolicy"),"No 1.6+ gesture changes");
        ok(!overlay.contains("PixelDirectionIcons"),"No later decorative icon code");
        ok(!overlay.contains("ClassicTopBarLayout"),"No later keyboard button");
        ok(overlay.contains("gesture.begin(x,y,event.getEventTime(),dp(8))"),"Original gameplay drag threshold preserved");
        System.out.println("OrientationOnly140Test: "+checks+" checks passed. No gameplay changes.");
    }
}
