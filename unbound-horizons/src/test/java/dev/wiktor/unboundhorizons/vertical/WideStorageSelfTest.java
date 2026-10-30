package dev.wiktor.unboundhorizons.vertical;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Plain Java self-test; no server dependencies, can run from CI with javac.
 */
public final class WideStorageSelfTest {
    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        final int edge = WideBlockPos.BORDER;
        Random random = new Random(0x554E424F554E444CL);

        // 150,000 complete XYZ round trips covering full border scale.
        for (int i = 0; i < 150_000; i++) {
            WideBlockPos value = new WideBlockPos(
                random.nextInt(-edge, edge + 1),
                random.nextInt(-edge, edge + 1),
                random.nextInt(-edge, edge + 1));
            expect(value.equals(WideBlockPos.fromBytes(value.toBytes())),
                "Full-range coordinate round-trip failed");
        }
        expect(new WideBlockPos(0, -1, 0).sectionY() == -1, "Negative section floorDiv");
        expect(new WideBlockPos(0, -16, 0).sectionY() == -1, "Negative 16 section");
        expect(new WideBlockPos(0, -17, 0).sectionY() == -2, "Negative 17 section");
        expect(new WideBlockPos(0, -edge, 0).sectionY() == -1_875_000,
            "Bottom border section");
        expect(new WideBlockPos(0, edge, 0).sectionY() == 1_875_000,
            "Top border section");

        Path temp = Files.createTempDirectory("unbound-wide-");
        WideBlockPos lower = new WideBlockPos(edge, -edge, -edge);
        WideBlockPos upper = new WideBlockPos(edge, edge, -edge);
        WideBlockPos oldCollision1 = new WideBlockPos(14, -3000, 14);
        WideBlockPos oldCollision2 = new WideBlockPos(14, 1096, 14);
        WideBlockPos atBottomNeighbor = new WideBlockPos(-edge, -edge + 1, edge);
        WideBlockPos atTopNeighbor = new WideBlockPos(-edge, edge - 1, edge);

        try (SparseVerticalStore store = new SparseVerticalStore(temp)) {
            store.set(lower, "minecraft:gold_block");
            store.set(upper, "minecraft:diamond_block");
            store.set(oldCollision1, "minecraft:deepslate");
            store.set(oldCollision2, "minecraft:glass");
            store.set(atBottomNeighbor, "minecraft:stone");
            store.set(atTopNeighbor, "minecraft:oak_planks");

            expect(store.get(lower).equals("minecraft:gold_block"), "Lower cached block");
            expect(store.get(upper).equals("minecraft:diamond_block"), "Upper cached block");
            expect(store.get(oldCollision1).equals("minecraft:deepslate"), "Old Y collision low");
            expect(store.get(oldCollision2).equals("minecraft:glass"), "Old Y collision high");

            // Force repeated LRU eviction; only nonempty sections exist on disk.
            for (int i = 0; i < 220; i++) {
                int y = -edge + i * 4096;
                store.set(new WideBlockPos(30, y, 40), "minecraft:iron_block");
                expect(store.cachedSectionCount() <= SparseVerticalStore.MAX_CACHED_SECTIONS,
                    "Sparse section cache exceeded cap");
            }
        }

        try (SparseVerticalStore reopened = new SparseVerticalStore(temp)) {
            expect(reopened.get(lower).equals("minecraft:gold_block"), "Lower persisted block");
            expect(reopened.get(upper).equals("minecraft:diamond_block"), "Upper persisted block");
            expect(reopened.get(oldCollision1).equals("minecraft:deepslate"), "Y collision persisted low");
            expect(reopened.get(oldCollision2).equals("minecraft:glass"), "Y collision persisted high");
            expect(reopened.get(atBottomNeighbor).equals("minecraft:stone"),
                "Bottom-neighbor persisted block");
            expect(reopened.get(atTopNeighbor).equals("minecraft:oak_planks"),
                "Top-neighbor persisted block");
            expect(reopened.get(new WideBlockPos(0, edge, 0)).equals(SparseVerticalStore.AIR),
                "Absent virtual block is air");

            reopened.set(lower, SparseVerticalStore.AIR);
            expect(reopened.get(lower).equals(SparseVerticalStore.AIR), "Block removal");
        }
        try (SparseVerticalStore reopened = new SparseVerticalStore(temp)) {
            expect(reopened.get(lower).equals(SparseVerticalStore.AIR),
                "Removal persists across restart");
            expect(reopened.get(upper).equals("minecraft:diamond_block"),
                "Unrelated top layer survived removal");
        }

        boolean rejected = false;
        try {
            new WideBlockPos(0, edge + 1, 0);
        } catch (IllegalArgumentException wanted) {
            rejected = true;
        }
        expect(rejected, "Positions outside border must be rejected");

        System.out.println("UNBOUND_WIDE_STORAGE_PASS: 150000 coordinate roundtrips, "
            + "both world-border Y extremes, signed sections, on-disk persistence, "
            + "old packed-Y collision avoidance, 220 LRU evictions, and deletion.");
        System.out.println("Self-test temp dir: " + temp);
    }
}
