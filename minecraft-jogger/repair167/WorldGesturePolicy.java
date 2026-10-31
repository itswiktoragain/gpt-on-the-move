package net.kdt.pojavlaunch.jogger.classic;

/**
 * Separate camera activation (responsive) from cancelling gameplay gestures
 * (finger-jitter tolerant). Pure logic; camera is controlled by launcher only.
 */
public final class WorldGesturePolicy {
    private WorldGesturePolicy() {}
    /** Deliberate taps and long-presses survive normal finger wobble. */
    public static float actionSlopDp() { return 17.0f; }
    /** A camera drag starts promptly without cancelling taps or mining. */
    public static float cameraSlopDp() { return 2.5f; }
    public static boolean rotate(boolean alreadyRotating, boolean holdingAction,
                                 float dxFromStart, float dyFromStart, float cameraThresholdPx) {
        return alreadyRotating || holdingAction
                || dxFromStart * dxFromStart + dyFromStart * dyFromStart
                        >= cameraThresholdPx * cameraThresholdPx;
    }
    /** If touch targeting is unavailable, retain vanilla crosshair interaction. */
    public static int correctHit(int hit, boolean raySupported, boolean offCentreTargetEnabled) {
        if(hit == 2 && (!raySupported || !offCentreTargetEnabled)) return 1;
        return hit;
    }
}
