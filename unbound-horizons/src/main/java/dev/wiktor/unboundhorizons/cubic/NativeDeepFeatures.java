package dev.wiktor.unboundhorizons.cubic;

import dev.wiktor.unboundhorizons.UnboundHorizons;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

/**
 * Unlike the virtual 30M cubic world, these are REAL worldgen additions for
 * vanilla/native Overworld chunks already supported by the 0.4.x Y=-4096
 * dimension. Ore placements run at deep negative Y, rather than being
 * silently restricted to vanilla's original -64 lower bound.
 *
 * Vanilla biome IDs still apply to these native chunks. The truly 3D
 * vertical biomes live in CubicTerrainGenerator until cubic integration.
 */
public final class NativeDeepFeatures {
    private NativeDeepFeatures() {}

    private static RegistryKey<PlacedFeature> key(String name) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE,
            Identifier.of(UnboundHorizons.MOD_ID, name));
    }

    public static void register() {
        for (String feature : new String[] {
            "deep_iron", "deep_copper", "deep_coal", "deep_gold",
            "deep_redstone", "deep_lapis", "deep_diamond", "deep_emerald"
        }) {
            BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_ORES,
                key(feature)
            );
        }
        for (String lake : new String[]{"deep_water_lake", "deep_lava_lake"}) {
            BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.LAKES,
                key(lake)
            );
        }
        UnboundHorizons.LOG.info("Registered deep native-world ores and occasional underground water/lava lakes.");
    }
}
