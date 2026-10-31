package net.kdt.pojavlaunch.jogger.mobile;

/** The rendering-only workaround taken from Jogger 1.5.1. */
public final class RenderSurfacePolicy {
    private RenderSurfacePolicy() {}
    public static boolean needsTextureView(int sdk, String manufacturer) {
        return sdk >= 35 && manufacturer != null && "samsung".equalsIgnoreCase(manufacturer.trim());
    }
    public static boolean migrateOnce(boolean previouslyMigrated, boolean affected) {
        return affected && !previouslyMigrated;
    }
    public static int evenScaled(int viewDimension, float scale) {
        if (viewDimension <= 0) return 0;
        int scaled = (int)(viewDimension * scale);
        return Math.max(2, scaled & ~1);
    }
}
