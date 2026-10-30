package dev.wiktor.unboundhorizons.cubic;

/**
 * 3D biome selection for the experimental independent cubic world.
 * These are generator categories, NOT registered vanilla Minecraft biomes.
 * The native client's biome rendering will require cubic-world integration.
 */
public enum CubicBiome {
    DEEP_SLATE("unboundhorizons:deep_slate"),
    DRIPSTONE_GROTTO("unboundhorizons:dripstone_grotto"),
    FLOODED_DEPTHS("unboundhorizons:flooded_depths"),
    MAGMA_CHAMBERS("unboundhorizons:magma_chambers"),
    CRYSTAL_CAVERNS("unboundhorizons:crystal_caverns"),
    LUSH_ABYSS("unboundhorizons:lush_abyss"),
    SCULK_TRENCH("unboundhorizons:sculk_trench"),
    BASALT_RIFTS("unboundhorizons:basalt_rifts"),
    SKY_ISLANDS("unboundhorizons:sky_islands");

    private final String id;
    CubicBiome(String id) { this.id = id; }
    public String id() { return id; }
}
