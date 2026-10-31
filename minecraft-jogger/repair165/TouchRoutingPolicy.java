package net.kdt.pojavlaunch.jogger.classic;

/** Camera input belongs to the launcher: its native pointer-grab state wins over late game-agent messages. */
public final class TouchRoutingPolicy {
    private TouchRoutingPolicy() {}

    public static String effectiveMode(boolean nativeGrabbed, String reported, String current) {
        if(nativeGrabbed) return "PLAY";
        // The game-side observer can be one or more ticks behind the GLFW grab callback.
        if("PLAY".equals(reported)) return "PLAY".equals(current) ? "SCREEN" : current;
        return reported == null ? current : reported;
    }
    /** Never accept a grab-change callback superseded by the latest native state. */
    public static boolean acceptGrabCallback(boolean reportedGrabbed, boolean nativeGrabbed) {
        return reportedGrabbed == nativeGrabbed;
    }
    /** Duplicate native grab notifications must not reset fingers mid-gesture. */
    public static boolean isNewGrabState(boolean grabbed, String mode) {
        return grabbed ? !"PLAY".equals(mode) : "PLAY".equals(mode);
    }
    /** A short move is a look; do not require the old 8dp dead zone. */
    public static float cameraSlopDp() { return 3f; }
    /** The classifier owns the hit category, not the initially held mouse key. */
    public static int holdFallbackHit(boolean rayKnown) { return rayKnown ? 2 : 1; }
}
