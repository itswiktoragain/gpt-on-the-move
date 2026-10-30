package dev.wiktor.unboundhorizons.cubic;

import dev.wiktor.unboundhorizons.vertical.SparseVerticalStore;
import dev.wiktor.unboundhorizons.vertical.VerticalWorldBorder;
import dev.wiktor.unboundhorizons.vertical.WideBlockPos;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sparse 3D cubic world. Only addressed 16x16x16 cubic chunks are generated
 * and retained; no world-height arrays, vertical chunk-column traversal
 * or 60-million-block allocations are used.
 *
 * Natural terrain is derived from (world seed, x, y, z). ONLY player edits
 * are persisted. Explicit AIR overlays preserve mined blocks on reload.
 *
 * IMPORTANT: still an experimental virtual world, not vanilla Minecraft's
 * native ServerWorld block store/render/lighting/network implementation.
 */
public final class CubicTerrainWorld implements AutoCloseable {
    public static final int CACHE_LIMIT = 128;
    private static final String FORMAT = "UNBOUND_CUBIC_WORLD_FORMAT=1";
    private record Key(int sx, int sy, int sz) {}

    private final CubicTerrainGenerator generator;
    private final SparseVerticalStore overrides;
    private final LinkedHashMap<Key, CubicChunk> cache =
        new LinkedHashMap<>(64, 0.75f, true);

    public CubicTerrainWorld(Path saveDirectory, long seed) throws IOException {
        Objects.requireNonNull(saveDirectory, "saveDirectory");
        Path absolute = saveDirectory.toAbsolutePath().normalize();
        Files.createDirectories(absolute);
        Path metadata = absolute.resolve("cubic.seed");
        String expected = FORMAT + "\n" + seed + "\n";
        try {
            Files.writeString(metadata, expected, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException exists) {
            String existing = Files.readString(metadata, StandardCharsets.UTF_8);
            if (!expected.equals(existing)) {
                throw new IOException("Existing cubic world has a different generator seed/version; "
                    + "refusing to mix incompatible terrain in " + absolute);
            }
        }
        this.generator = new CubicTerrainGenerator(seed);
        this.overrides = new SparseVerticalStore(absolute.resolve("edits"));
    }

    public long seed() { return generator.seed(); }

    public synchronized String getNatural(WideBlockPos pos) {
        VerticalWorldBorder.requireInsidePlayableY(pos.y());
        return cubeAt(pos).blockId(pos);
    }

    public synchronized CubicBiome biomeAt(WideBlockPos pos) {
        VerticalWorldBorder.requireInsidePlayableY(pos.y());
        return cubeAt(pos).biome();
    }

    public synchronized String get(WideBlockPos pos) throws IOException {
        VerticalWorldBorder.requireInsidePlayableY(pos.y());
        String edited = overrides.getOverride(pos);
        return edited == null ? getNatural(pos) : edited;
    }

    public synchronized void set(WideBlockPos pos, String blockId) throws IOException {
        VerticalWorldBorder.requireInsidePlayableY(pos.y());
        Objects.requireNonNull(blockId, "blockId");
        String natural = getNatural(pos);
        if (natural.equals(blockId)) {
            overrides.removeOverride(pos);
        } else {
            overrides.setOverride(pos,blockId);
        }
    }

    /**
     * Mine a block to air, including naturally generated rock. This records
     * explicit air rather than treating missing edits as empty space.
     */
    public synchronized void mine(WideBlockPos pos) throws IOException {
        set(pos, CubicBlocks.id(CubicBlocks.AIR));
    }

    /** Restore the procedurally generated base at this location. */
    public synchronized void restoreNatural(WideBlockPos pos) throws IOException {
        overrides.removeOverride(pos);
    }

    public synchronized CubicChunk getChunk(int sx,int sy,int sz) {
        Key key = new Key(sx,sy,sz);
        CubicChunk chunk=cache.get(key);
        if (chunk != null) return chunk;
        chunk=generator.generate(sx,sy,sz);
        cache.put(key,chunk);
        while (cache.size()>CACHE_LIMIT) {
            Iterator<Key> it=cache.keySet().iterator();
            it.next();
            it.remove();
        }
        return chunk;
    }

    public synchronized int cachedCubeCount() { return cache.size(); }
    public synchronized int cachedEditSectionCount() { return overrides.cachedSectionCount(); }

    private CubicChunk cubeAt(WideBlockPos pos) {
        return getChunk(pos.sectionX(),pos.sectionY(),pos.sectionZ());
    }

    @Override public synchronized void close() {
        overrides.close();
        cache.clear();
    }
}
