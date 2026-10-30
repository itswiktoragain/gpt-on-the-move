package dev.wiktor.unboundhorizons.vertical;

/**
 * Coordinate policy for the proposed +-30-million-Y engine.
 *
 * The actual playable border is 16 blocks BEFORE the hard integer-world
 * boundary. As at the horizontal world border, the physical barrier is
 * encountered first; the 30,000,000 location is an independent safety cap.
 *
 * This class is independent of vanilla's (currently much shorter) world
 * dimensions. The sparse storage backend and future cubic-world movement
 * logic share the same constants.
 */
public final class VerticalWorldBorder {
    public static final int HARD_LIMIT = 30_000_000;
    public static final int BORDER_MARGIN = 16;
    public static final int ACTUAL_BORDER = HARD_LIMIT - BORDER_MARGIN;
    public static final int MIN_PLAYABLE_Y = -ACTUAL_BORDER;
    public static final int MAX_PLAYABLE_Y = ACTUAL_BORDER;
    public static final int HARD_MIN_Y = -HARD_LIMIT;
    public static final int HARD_MAX_Y = HARD_LIMIT;

    private VerticalWorldBorder() {}

    /** True on the actual playable border, not in the 16-block buffer. */
    public static boolean isInsidePlayableY(int y) {
        return y >= MIN_PLAYABLE_Y && y <= MAX_PLAYABLE_Y;
    }

    /** Hard-stop coordinates are technically representable but not playable. */
    public static boolean isHardStopReached(double y) {
        return !Double.isFinite(y) || y <= HARD_MIN_Y || y >= HARD_MAX_Y;
    }

    /**
     * Safety-first player-foot position, blocking downward motion as soon
     * as the player reaches -29,999,984, never at the later -30M cap.
     */
    public static double clampPlayerFeetY(double y) {
        if (!Double.isFinite(y)) {
            throw new IllegalArgumentException("Nonfinite vertical position");
        }
        return Math.max(MIN_PLAYABLE_Y, Math.min(MAX_PLAYABLE_Y, y));
    }

    /**
     * Used for writes AND reads. No block data may live beyond the actual
     * playable Y border, even though the wide coordinate can represent
     * the extra 16 technical safety-block positions.
     */
    public static void requireInsidePlayableY(int y) {
        if (!isInsidePlayableY(y)) {
            throw new IllegalArgumentException(
                "Y=" + y + " is beyond vertical world border " +
                MIN_PLAYABLE_Y + ".." + MAX_PLAYABLE_Y +
                " (hard stop at +/-" + HARD_LIMIT + ")"
            );
        }
    }
}
