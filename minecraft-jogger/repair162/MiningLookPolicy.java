package net.kdt.pojavlaunch.jogger.classic;

/** Separate ordinary camera swipes from a held block-mining gesture. */
public final class MiningLookPolicy {
    private MiningLookPolicy() {}

    /** Camera motion alone must not clear the touched block's ray. */
    public static boolean clearTargetOnMove(boolean miningStarted) {
        return !miningStarted;
    }

    /** Ignore a classification reply that became stale during a swipe. */
    public static boolean applyDelayedClassification(boolean fingerMoved) {
        return !fingerMoved;
    }
}
