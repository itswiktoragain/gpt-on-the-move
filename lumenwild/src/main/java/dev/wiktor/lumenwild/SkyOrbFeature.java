package dev.wiktor.lumenwild;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/** Glowing, freely floating meteorite spheres that form between the islands. */
public final class SkyOrbFeature extends Feature<DefaultFeatureConfig> {
    public SkyOrbFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        Random random = context.getRandom();
        BlockPos origin = context.getOrigin();
        if (origin.getY() < 12 || origin.getY() > 306) return false;
        if (!world.getBlockState(origin).isAir()
            || !world.getBlockState(origin.up(6)).isAir()
            || !world.getBlockState(origin.down(6)).isAir()) return false;

        int radius = 3 + random.nextInt(3);
        int yRadius = radius + random.nextInt(2);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -yRadius; dy <= yRadius; dy++) {
                    double squared = (dx * dx + dz * dz) / (double) (radius * radius)
                        + (dy * dy) / (double) (yRadius * yRadius);
                    if (squared > 1.05) continue;
                    BlockPos pos = origin.add(dx,dy,dz);
                    if (pos.getY() >= 315 || pos.getY() < -60) continue;
                    BlockState state;
                    if (squared < 0.20) {
                        state = LumenwildMod.HALO_CRYSTAL.getDefaultState();
                    } else if (squared > 0.68 && random.nextInt(5) == 0) {
                        state = LumenwildMod.PRISM_BLOCK.getDefaultState();
                    } else if (random.nextInt(4) == 0) {
                        state = LumenwildMod.CRYSTAL_BLOOM.getDefaultState();
                    } else {
                        state = LumenwildMod.AETHERSTONE.getDefaultState();
                    }
                    world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
                }
            }
        }
        return true;
    }
}
