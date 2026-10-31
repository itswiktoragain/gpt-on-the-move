package net.kdt.pojavlaunch.jogger.classic;

/** Decide when camera motion is intentional, independent of mining ownership. */
public final class CameraLookPolicy {
    private CameraLookPolicy() {}

    /** Keep tap slop before a hold, but never lock the view once mining. */
    public static boolean shouldRotate(boolean movedBeyondSlop, boolean mining, boolean attackHeld) {
        return movedBeyondSlop || (mining && attackHeld);
    }

    /** True for a second touch on empty world space while the first is held. */
    public static boolean routeSecondLook(boolean playing, boolean primaryFingerActive, boolean lookFingerActive) {
        return playing && primaryFingerActive && !lookFingerActive;
    }
}
