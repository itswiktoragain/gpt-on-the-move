package net.kdt.pojavlaunch.jogger.classic;

/** Square pixel-art arrows with a consistent, screen-aligned shadow in the overlay. */
public final class PixelDirectionIcons {
    private PixelDirectionIcons() {}
    // The original seven-cell-wide up arrow is retained exactly.
    public static final String[] UP = {
        "0001000", "0011100", "0111110", "1111111",
        "0001000", "0001000", "0001000"
    };
    public static final String[] RIGHT = rotateCW(UP);
    public static final String[] DOWN = rotateCW(RIGHT);
    public static final String[] LEFT = rotateCW(DOWN);

    // NW arrowhead is 4 cells deep. Below the head, the shaft is ONE cell
    // across at every step. No 2- or 3-pixel wide diagonal strip.
    public static final String[] UP_LEFT = {
        "1111100",
        "1100000",
        "1010000",
        "1001000",
        "0000100",
        "0000010",
        "0000001"
    };
    public static final String[] UP_RIGHT = mirrorX(UP_LEFT);

    private static String[] rotateCW(String[] a) {
        int n = a.length;
        String[] out = new String[n];
        for (int y = 0; y < n; y++) {
            StringBuilder line = new StringBuilder(n);
            for (int x = 0; x < n; x++) line.append(a[n-1-x].charAt(y));
            out[y] = line.toString();
        }
        return out;
    }
    private static String[] mirrorX(String[] a) {
        String[] out = new String[a.length];
        for (int y=0; y<a.length; y++) out[y] = new StringBuilder(a[y]).reverse().toString();
        return out;
    }
}
