package dev.wiktor.unboundhorizons.vertical;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A collision-free, three-int coordinate for the experimental vertical engine.
 *
 * Minecraft's 64-bit BlockPos encoding cannot simultaneously preserve
 * X/Z near the world border AND +/-30 million Y. This type deliberately
 * avoids packing three coordinates into a single long.
 *
 * Coordinates are immutable and serialise as three signed 32-bit integers
 * (12 bytes). Each axis retains its full technical 30M range; vertical PLAYABLE coordinates end at +/-29,999,984.
 */
public record WideBlockPos(int x, int y, int z) {
    public static final int BORDER = 30_000_000;
    public static final int SERIALIZED_BYTES = 12;

    public WideBlockPos {
        if (x < -BORDER || x > BORDER || y < -BORDER || y > BORDER
                || z < -BORDER || z > BORDER) {
            throw new IllegalArgumentException(
                "Coordinate outside world-border-sized range [-30000000,30000000]: "
                + x + ", " + y + ", " + z
            );
        }
    }

    public int sectionX() {
        return Math.floorDiv(x, 16);
    }

    public int sectionY() {
        return Math.floorDiv(y, 16);
    }

    public int sectionZ() {
        return Math.floorDiv(z, 16);
    }

    public int sectionBlockIndex() {
        // Index 0..4095; works correctly at negative heights as well.
        return ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
    }

    public byte[] toBytes() {
        return ByteBuffer.allocate(SERIALIZED_BYTES).order(ByteOrder.BIG_ENDIAN)
            .putInt(x).putInt(y).putInt(z).array();
    }

    public static WideBlockPos fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != SERIALIZED_BYTES) {
            throw new IllegalArgumentException("Expected exactly 12 coordinate bytes");
        }
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        return new WideBlockPos(b.getInt(), b.getInt(), b.getInt());
    }
}
