package dev.wiktor.unboundhorizons.vertical;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.regex.Pattern;

/**
 * Disk-backed sparse 16x16x16 block sections with full signed-int
 * section coordinates. There is no preallocated 60M-high section array.
 *
 * This is infrastructure for a future cubic-chunk engine, NOT Y-world
 * integration: vanilla chunks, lighting, rendering and physics do not
 * use this store yet.
 *
 * Sections are loaded lazily and written atomically on modification.
 * Only MAX_CACHED_SECTIONS sections are held in memory concurrently.
 */
public final class SparseVerticalStore implements AutoCloseable {
    public static final int MAX_CACHED_SECTIONS = 128;
    public static final String AIR = "minecraft:air";
    private static final int MAGIC = 0x55485753;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_BLOCKS_PER_SECTION = 4096;
    private static final Pattern BLOCK_ID = Pattern.compile(
        "[a-z0-9_.-]+:[a-z0-9_./-]+"
    );

    private record SectionKey(int sx, int sy, int sz) {}

    private final Path baseDirectory;
    private final LinkedHashMap<SectionKey, HashMap<Integer, String>> sections =
        new LinkedHashMap<>(32, 0.75f, true);

    public SparseVerticalStore(Path worldScopedDirectory) {
        baseDirectory = Objects.requireNonNull(worldScopedDirectory, "worldScopedDirectory")
            .toAbsolutePath().normalize();
    }

    public synchronized String get(WideBlockPos pos) throws IOException {
        VerticalWorldBorder.requireInsidePlayableY(pos.y());
        return load(key(pos)).getOrDefault(pos.sectionBlockIndex(), AIR);
    }

    public synchronized void set(WideBlockPos pos, String blockId) throws IOException {
        VerticalWorldBorder.requireInsidePlayableY(pos.y());
        requireBlockId(blockId);
        SectionKey key = key(pos);
        HashMap<Integer, String> blocks = load(key);
        int index = pos.sectionBlockIndex();
        String previous = blocks.getOrDefault(index, AIR);
        if (previous.equals(blockId)) return;

        if (blockId.equals(AIR)) blocks.remove(index);
        else blocks.put(index, blockId);

        try {
            persist(key, blocks);
        } catch (IOException e) {
            // If the atomic write failed, restore previous in-memory state.
            if (previous.equals(AIR)) blocks.remove(index);
            else blocks.put(index, previous);
            throw e;
        }
    }

    public synchronized int cachedSectionCount() {
        return sections.size();
    }

    @Override
    public synchronized void close() {
        sections.clear();
    }

    private static void requireBlockId(String id) {
        if (id == null || id.length() > 192 || !BLOCK_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid block identifier: " + id);
        }
    }

    private static SectionKey key(WideBlockPos pos) {
        return new SectionKey(pos.sectionX(), pos.sectionY(), pos.sectionZ());
    }

    private HashMap<Integer, String> load(SectionKey key) throws IOException {
        HashMap<Integer, String> alreadyLoaded = sections.get(key);
        if (alreadyLoaded != null) return alreadyLoaded;

        HashMap<Integer, String> blocks = new HashMap<>();
        Path file = pathFor(key);
        if (Files.isRegularFile(file)) {
            try (DataInputStream input = new DataInputStream(
                    new GZIPInputStream(Files.newInputStream(file)))) {
                int magic = input.readInt();
                int version = input.readInt();
                int x = input.readInt();
                int y = input.readInt();
                int z = input.readInt();
                int count = input.readInt();

                if (magic != MAGIC || version != FORMAT_VERSION
                        || x != key.sx() || y != key.sy() || z != key.sz()
                        || count < 0 || count > MAX_BLOCKS_PER_SECTION) {
                    throw new IOException("Invalid sparse section header: " + file);
                }
                for (int i = 0; i < count; i++) {
                    int blockIndex = input.readUnsignedShort();
                    String id = input.readUTF();
                    if (blockIndex >= MAX_BLOCKS_PER_SECTION) {
                        throw new IOException("Invalid sparse block index");
                    }
                    try {
                        requireBlockId(id);
                    } catch (IllegalArgumentException badBlock) {
                        throw new IOException("Invalid stored block id", badBlock);
                    }
                    if (AIR.equals(id) || blocks.putIfAbsent(blockIndex, id) != null) {
                        throw new IOException("Duplicate or empty sparse entry in " + file);
                    }
                }
                if (input.read() != -1) {
                    throw new IOException("Trailing bytes in sparse section: " + file);
                }
            }
        }
        sections.put(key, blocks);
        while (sections.size() > MAX_CACHED_SECTIONS) {
            Iterator<SectionKey> it = sections.keySet().iterator();
            it.next();
            it.remove();
        }
        return blocks;
    }

    private Path pathFor(SectionKey key) {
        // Shard by X and Z. Each Y is a separate file, so even a
        // 60-million-block-tall dimension does not allocate empty levels.
        return baseDirectory.resolve("v1")
            .resolve(Integer.toString(Math.floorMod(key.sx(), 64)))
            .resolve(Integer.toString(Math.floorMod(key.sz(), 64)))
            .resolve(key.sx() + "_" + key.sz())
            .resolve(key.sy() + ".uhs.gz");
    }

    private void persist(SectionKey key, Map<Integer, String> blocks) throws IOException {
        Path target = pathFor(key);
        if (blocks.isEmpty()) {
            Files.deleteIfExists(target);
            return;
        }
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "section-", ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(MAGIC);
                out.writeInt(FORMAT_VERSION);
                out.writeInt(key.sx());
                out.writeInt(key.sy());
                out.writeInt(key.sz());
                out.writeInt(blocks.size());
                for (Map.Entry<Integer, String> entry : new TreeMap<>(blocks).entrySet()) {
                    out.writeShort(entry.getKey());
                    out.writeUTF(entry.getValue());
                }
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
