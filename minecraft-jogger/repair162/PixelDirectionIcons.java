package net.kdt.pojavlaunch.jogger.classic;

/**
 * Screen-aligned, seven-by-seven bitmap arrows. The original UP sprite is the
 * single source for the cardinal directions, including horizontal movement.
 * Diagonals are hand-rasterized on the SAME grid and match the width and weight
 * of the horizontal sprites. The Android Canvas must never rotate these icons:
 * their screen-space bottom-right shadows would rotate too.
 */
public final class PixelDirectionIcons {
    private PixelDirectionIcons() {}

    public static final String[] UP = {
        "0001000",
        "0011100",
        "0111110",
        "1111111",
        "0001000",
        "0001000",
        "0001000"
    };
    public static final String[] RIGHT = rotateCW(UP);
    public static final String[] DOWN = rotateCW(RIGHT);
    public static final String[] LEFT = rotateCW(DOWN);

    // A square-cell 45-degree adaptation of the cardinal arrow: thick
    // block-pixel arrowhead and a continuous two-pixel diagonal shaft.
    public static final String[] UP_LEFT = {
        "1111100",
        "1111000",
        "1110000",
        "1101100",
        "1001110",
        "0000111",
        "0000011"
    };
    public static final String[] UP_RIGHT = mirrorX(UP_LEFT);

    private static String[] rotateCW(String[] source) {
        int n=source.length;
        String[] result=new String[n];
        for(int y=0;y<n;y++) {
            StringBuilder row=new StringBuilder(n);
            for(int x=0;x<n;x++)row.append(source[n-1-x].charAt(y));
            result[y]=row.toString();
        }
        return result;
    }
    private static String[] mirrorX(String[] source) {
        String[] result=new String[source.length];
        for(int y=0;y<source.length;y++)
            result[y]=new StringBuilder(source[y]).reverse().toString();
        return result;
    }
}
