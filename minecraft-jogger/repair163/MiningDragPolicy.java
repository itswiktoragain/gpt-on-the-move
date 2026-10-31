package net.kdt.pojavlaunch.jogger.classic;

/**
 * A world-view drag is not a finger-up. Continue mining for as long as the
 * same gesture still owns a pressed left mouse button. Keeping the ray alone
 * is insufficient: releasing Inputs owner -3 ends Minecraft's break action.
 */
public final class MiningDragPolicy {
    private MiningDragPolicy() {}

    public static boolean keepAttackWhileLooking(boolean longHoldStarted, boolean attackHeld) {
        return longHoldStarted && attackHeld;
    }
}
