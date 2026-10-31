package net.kdt.pojavlaunch.jogger;

/**
 * Ignore out-of-date mouse echo packets from the game JVM.
 *
 * With an asynchronous socket, a DOWN acknowledgement may arrive after a
 * finger's UP. Never re-apply that old DOWN over the newer desired state.
 */
public final class MouseAckPolicy {
    private MouseAckPolicy() {}

    public static boolean shouldForward(int button, boolean acknowledgedDown, boolean desiredDown) {
        // Only the mining (left) mouse button needs the strict stale-echo gate.
        return button != 0 || acknowledgedDown == desiredDown;
    }
}
