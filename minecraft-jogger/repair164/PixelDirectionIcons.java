package net.kdt.pojavlaunch.jogger.classic;

/** Original cardinal style, plus filled 45 degree arrowheads and one-cell shafts. */
public final class PixelDirectionIcons {
    private PixelDirectionIcons() {}
    public static final String[] UP = {
        "0001000", "0011100", "0111110", "1111111",
        "0001000", "0001000", "0001000"
    };
    public static final String[] RIGHT = rotateCW(UP);
    public static final String[] DOWN = rotateCW(RIGHT);
    public static final String[] LEFT = rotateCW(DOWN);

    // The triangle x+y<=4 has 15 filled cells and matches the cardinal
    // arrowhead weight. Only a one-cell shaft continues from (3,3) to (6,6).
    // 15 + 4 = 19 foreground pixels, the exact count of the cardinal arrows.
    public static final String[] UP_LEFT = {
        "1111100",
        "1111000",
        "1110000",
        "1101000",
        "1000100",
        "0000010",
        "0000001"
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
