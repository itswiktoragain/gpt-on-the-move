package net.kdt.pojavlaunch.jogger.classic;

/**
 * Orthogonal raster artwork for the two diagonal D-pad arrows.
 * Every "1" is an axis-aligned square cell. No canvas rotation.
 */
public final class PixelDiagonalIcons {
    private PixelDiagonalIcons() {}

    // Top-left arrow: 2-cell stepped diagonal shaft and blocky arrowhead.
    public static final String[] UP_LEFT = {
        "111110000",
        "110000000",
        "101000000",
        "100110000",
        "100011000",
        "000001100",
        "000000110",
        "000000011",
        "000000001"
    };
    public static final String[] UP_RIGHT = {
        "000011111",
        "000000011",
        "000000101",
        "000011001",
        "000110001",
        "001100000",
        "011000000",
        "110000000",
        "100000000"
    };
}
