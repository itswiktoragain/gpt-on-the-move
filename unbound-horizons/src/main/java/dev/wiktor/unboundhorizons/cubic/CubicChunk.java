package dev.wiktor.unboundhorizons.cubic;

import dev.wiktor.unboundhorizons.vertical.WideBlockPos;
import java.util.Arrays;

/**
 * One 16x16x16 cubic chunk, addressed by (chunkX, chunkY, chunkZ).
 * Memory cost: 4096 palette indices, irrespective of world height.
 */
public final class CubicChunk {
    public static final int SIDE = 16;
    public static final int VOLUME = SIDE * SIDE * SIDE;

    private final int chunkX, chunkY, chunkZ;
    private final CubicBiome biome;
    private final byte[] blocks;

    public CubicChunk(int chunkX, int chunkY, int chunkZ, CubicBiome biome, byte[] blocks) {
        if (blocks == null || blocks.length != VOLUME) {
            throw new IllegalArgumentException("Expected exactly 4096 block indices");
        }
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;
        this.biome = biome;
        this.blocks = Arrays.copyOf(blocks, VOLUME);
    }

    public int x() { return chunkX; }
    public int y() { return chunkY; }
    public int z() { return chunkZ; }
    public CubicBiome biome() { return biome; }

    public byte block(int localX, int localY, int localZ) {
        if ((localX | localY | localZ) < 0 || localX > 15 || localY > 15 || localZ > 15) {
            throw new IndexOutOfBoundsException("Local block coords outside [0..15]");
        }
        return blocks[(localY << 8) | (localZ << 4) | localX];
    }

    public String blockId(WideBlockPos pos) {
        if (pos.sectionX() != chunkX || pos.sectionY() != chunkY || pos.sectionZ() != chunkZ) {
            throw new IllegalArgumentException("Position belongs to a different cubic chunk");
        }
        return CubicBlocks.id(block(pos.x() & 15, pos.y() & 15, pos.z() & 15));
    }

    public int count(byte type) {
        int count=0;
        for (byte block : blocks) if(block == type) count++;
        return count;
    }

    public int oreCount() {
        int count=0;
        for (byte block : blocks) if(CubicBlocks.isOre(block)) count++;
        return count;
    }

    public int fluidCount() {
        int count=0;
        for (byte block : blocks) if(CubicBlocks.isFluid(block)) count++;
        return count;
    }

    public byte[] blocksCopy() { return blocks.clone(); }

    public static String sectionLocation(WideBlockPos pos) {
        return pos.sectionX() + "," + pos.sectionY() + "," + pos.sectionZ();
    }
}
