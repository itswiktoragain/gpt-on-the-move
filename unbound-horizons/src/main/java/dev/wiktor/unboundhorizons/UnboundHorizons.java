package dev.wiktor.unboundhorizons;

import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The vertical range is the maximum vanilla 1.21.4 dimension range;
 * it is not infinite and requires a custom game engine to go further.
 */
public final class UnboundHorizons implements ModInitializer {
    public static final String MOD_ID = "unboundhorizons";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    public static final boolean VERIFY_TERRAIN = Boolean.getBoolean("unboundhorizons.verifyTerrain");
    public static final AtomicInteger DEEP_AIR_SAMPLES = new AtomicInteger();

    @Override
    public void onInitialize() {
        LOG.info("Unbound Horizons: no built-in LOD, deep Overworld noise Y=-2032..319 and full dimension Y=-2032..2031.");
        if (VERIFY_TERRAIN) {
            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                var world = server.getOverworld();
                BlockPos spawn = world.getSpawnPos();
                int[] depths = {-80, -256, -512, -768, -1200, -1600, -1952};
                int[] solids = new int[depths.length];
                int[] lavas = new int[depths.length];
                int[] airs = new int[depths.length];
                int samplesPerDepth = 0;
                for (int dx = -12; dx <= 12; dx += 6) {
                    for (int dz = -12; dz <= 12; dz += 6) {
                        for (int i = 0; i < depths.length; i++) {
                            BlockState state = world.getBlockState(
                                new BlockPos(spawn.getX() + dx, depths[i], spawn.getZ() + dz)
                            );
                            if (state.isAir()) airs[i]++;
                            else if (state.getFluidState().isOf(Fluids.LAVA)) lavas[i]++;
                            else if (state.getFluidState().isEmpty()) solids[i]++;
                        }
                        samplesPerDepth++;
                    }
                }
                for(int i=0;i<depths.length;i++) {
                    LOG.info("UNBOUND_DEPTH_SAMPLE Y={} solid={} air={} lava={} samples={}",
                        depths[i], solids[i], airs[i], lavas[i], samplesPerDepth);
                }
                int deepAir = DEEP_AIR_SAMPLES.get();
                LOG.info("UNBOUND_AQUIFER_AIR_OVERRIDES {}",deepAir);
                boolean rockEverywhere=true;
                boolean noLava=true;
                for(int i=0;i<depths.length;i++) {
                    rockEverywhere &= solids[i]>0;
                    noLava &= lavas[i]==0;
                }
                if(rockEverywhere && noLava && deepAir>0){
                    LOG.info("UNBOUND_TERRAIN_CHECK_PASS");
                }else{
                    LOG.error("UNBOUND_TERRAIN_CHECK_FAIL: rock={}, noLava={}, airOverrides={}",
                        rockEverywhere,noLava,deepAir);
                }
            });
        }
    }
}
