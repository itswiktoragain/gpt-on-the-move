import net.kdt.pojavlaunch.jogger.mobile.RenderSurfacePolicy;
public final class RenderSurfacePolicyTest {
    private static int checks;
    private static void check(boolean good, String label) {
        if (!good) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) {
        check(RenderSurfacePolicy.needsTextureView(36, "samsung"), "Samsung Android 16");
        check(RenderSurfacePolicy.needsTextureView(35, "SAMSUNG"), "Samsung Android 15");
        check(!RenderSurfacePolicy.needsTextureView(34, "samsung"), "older Samsung unaffected");
        check(!RenderSurfacePolicy.needsTextureView(36, "Google"), "other OEM unaffected");
        check(!RenderSurfacePolicy.needsTextureView(36, null), "null manufacturer safe");
        check(RenderSurfacePolicy.migrateOnce(false, true), "migrate affected device");
        check(!RenderSurfacePolicy.migrateOnce(true, true), "allow user override after migration");
        check(!RenderSurfacePolicy.migrateOnce(false, false), "unaffected preference retained");
        check(RenderSurfacePolicy.evenScaled(2240, 1f)==2240, "width");
        check(RenderSurfacePolicy.evenScaled(1080, .7f)==756, "scaled height");
        check(RenderSurfacePolicy.evenScaled(2241, 1f)==2240, "round to even");
        check(RenderSurfacePolicy.evenScaled(0, 1f)==0, "zero dimensions");
        System.out.println("RenderSurfacePolicy: "+checks+" checks passed");
    }
}
