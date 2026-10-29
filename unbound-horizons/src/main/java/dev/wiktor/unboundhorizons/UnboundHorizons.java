package dev.wiktor.unboundhorizons;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric's shared entrypoint. */
public final class UnboundHorizons implements ModInitializer {
    public static final String MOD_ID = "unboundhorizons";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOG.info("Unbound Horizons: vertical build range -512..1535, Overworld noise -512..319.");
        // A real world-generation validation, enabled only in the CI server.
        // This checks that BELOW -64 actual worldgen has solid blocks, rather
        // than merely accepting block placement or a dimension registry value.
        if (Boolean.getBoolean("unboundhorizons.verifyTerrain")) {
            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                var world = server.getOverworld();
                BlockPos spawn = world.getSpawnPos();
                int[] heights = {-80, -160, -320, -480};
                int[] found = new int[heights.length];
                int samplesPerHeight = 0;
                for (int dx = -16; dx <= 16; dx += 8) {
                    for (int dz = -16; dz <= 16; dz += 8) {
                        for (int i = 0; i < heights.length; i++) {
                            BlockState state = world.getBlockState(
                                new BlockPos(spawn.getX() + dx, heights[i], spawn.getZ() + dz)
                            );
                            if (!state.isAir() && state.getFluidState().isEmpty()) found[i]++;
                        }
                        samplesPerHeight++;
                    }
                }
                LOG.info("UNBOUND_TERRAIN_CHECK detail: samples per depth={}, Y-80={}, Y-160={}, Y-320={}, Y-480={}",
                    samplesPerHeight, found[0], found[1], found[2], found[3]);
                if (found[0] > 0 && found[1] > 0 && found[2] > 0 && found[3] > 0) {
                    LOG.info("UNBOUND_TERRAIN_CHECK_PASS");
                } else {
                    LOG.error("UNBOUND_TERRAIN_CHECK_FAIL: Some extended vertical layers contain no solid terrain!");
                }
            });
        }
    }
}
