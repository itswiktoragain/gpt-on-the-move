package dev.wiktor.unboundhorizons;

import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UnboundHorizons implements ModInitializer {
    public static final String MOD_ID = "unboundhorizons";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    public static final boolean VERIFY_TERRAIN = Boolean.getBoolean("unboundhorizons.verifyTerrain");
    public static final boolean VERIFY_RELOAD = Boolean.getBoolean("unboundhorizons.verifyReload");
    public static final AtomicInteger DEEP_AIR_SAMPLES = new AtomicInteger();
    private static final int MIN_Y = -4096;
    private static final int DIMENSION_HEIGHT = 8192;

    @Override
    public void onInitialize() {
        LOG.info("Unbound Horizons height-engine experiment: -4096..4095; no built-in LOD.");
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var world = server.getOverworld();

            // The new 25-bit packed X/Z layout supports +/-16.7 million.
            // Reduce the *diameter* of excessively large borders to 30 million.
            // This prevents silently wrapping block coordinates beyond the cap.
            if (world.getWorldBorder().getSize() > 30_000_000.0) {
                world.getWorldBorder().setSize(30_000_000.0);
                LOG.warn("Reduced world border diameter to 30M to prevent 25-bit packed X/Z aliasing.");
            }

            if (!VERIFY_TERRAIN && !VERIFY_RELOAD) return;
            BlockPos spawn = world.getSpawnPos();
            int x = spawn.getX() + 3;
            int z = spawn.getZ() + 3;
            BlockPos lower = new BlockPos(x, -3000, z);
            BlockPos upper = new BlockPos(x, 1096, z);
            boolean packing =
                BlockPos.SIZE_BITS_Y == 14
                && BlockPos.fromLong(lower.asLong()).equals(lower)
                && BlockPos.fromLong(upper.asLong()).equals(upper)
                && lower.asLong() != upper.asLong();
            boolean bounds = world.getBottomY() == MIN_Y
                && world.getHeight() == DIMENSION_HEIGHT
                && world.getTopYInclusive() == 4095
                && DimensionType.MAX_HEIGHT > 8192;
            LOG.info("UNBOUND_HEIGHT_CHECK bitsY={} dimensionBottom={} dimensionTop={} packing={} bounds={}",
                BlockPos.SIZE_BITS_Y, world.getBottomY(), world.getTopYInclusive(), packing, bounds);

            if(VERIFY_RELOAD) {
                boolean saved = world.getBlockState(lower).isOf(Blocks.GOLD_BLOCK)
                    && world.getBlockState(upper).isOf(Blocks.DIAMOND_BLOCK);
                if(saved && packing && bounds) LOG.info("UNBOUND_SAVE_RELOAD_PASS");
                else LOG.error("UNBOUND_SAVE_RELOAD_FAIL: saved={} packing={} bounds={}",saved,packing,bounds);
                return;
            }

            int[] depths={-80,-512,-2048,-3000,-4032};
            int[] solids=new int[depths.length];
            int[] lavas=new int[depths.length];
            int[] airs=new int[depths.length];
            for (int dx=-12; dx<=12; dx+=6) {
                for (int dz=-12; dz<=12; dz+=6) {
                    for (int i=0;i<depths.length;i++) {
                        BlockState state=world.getBlockState(new BlockPos(spawn.getX()+dx,depths[i],spawn.getZ()+dz));
                        if(state.isAir()) airs[i]++;
                        else if(state.getFluidState().isOf(Fluids.LAVA)) lavas[i]++;
                        else if(state.getFluidState().isEmpty()) solids[i]++;
                    }
                }
            }
            boolean generated=true, noLava=true;
            for(int i=0;i<depths.length;i++){
                LOG.info("UNBOUND_DEPTH_SAMPLE Y={} solid={} air={} lava={}", depths[i],solids[i],airs[i],lavas[i]);
                generated &= solids[i]>0;
                noLava &= lavas[i]==0;
            }
            boolean modified = world.setBlockState(lower,Blocks.GOLD_BLOCK.getDefaultState(),Block.NOTIFY_ALL)
                && world.setBlockState(upper,Blocks.DIAMOND_BLOCK.getDefaultState(),Block.NOTIFY_ALL)
                && world.getBlockState(lower).isOf(Blocks.GOLD_BLOCK)
                && world.getBlockState(upper).isOf(Blocks.DIAMOND_BLOCK);
            LOG.info("UNBOUND_ENGINE_TEST deepGen={} noDeepLava={} airOverrides={} blockStateRoundTrip={}",
                generated,noLava,DEEP_AIR_SAMPLES.get(),modified);
            if(bounds && packing && generated && noLava && DEEP_AIR_SAMPLES.get()>0 && modified) {
                LOG.info("UNBOUND_ENGINE_HEIGHT_CHECK_PASS");
            }else{
                LOG.error("UNBOUND_ENGINE_HEIGHT_CHECK_FAIL");
            }
        });
    }
}
