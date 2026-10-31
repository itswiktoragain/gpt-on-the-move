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

/**
 * Procedural, twisted, glowing crystal needles with several jutting arms.
 * Generated as original 3D geometry; not copied from vanilla trees.
 */
public final class CrystalSpireFeature extends Feature<DefaultFeatureConfig> {
    public CrystalSpireFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        Random random = context.getRandom();
        BlockPos origin = context.getOrigin();
        if (!world.getBlockState(origin).isAir()) return false;
        BlockState support = world.getBlockState(origin.down());
        if (!(support.isOf(LumenwildMod.STAR_MOSS)
                || support.isOf(LumenwildMod.LUMENMOSS)
                || support.isOf(LumenwildMod.SHADOW_MOSS)
                || support.isOf(LumenwildMod.AETHERSTONE))) return false;

        int height = 15 + random.nextInt(26);
        int baseRadius = 2 + random.nextInt(3);
        double bendX = (random.nextFloat() - 0.5) * 5.5;
        double bendZ = (random.nextFloat() - 0.5) * 5.5;

        for (int y = 0; y < height; y++) {
            double t = y / (double) height;
            int cx = origin.getX() + (int) Math.round(bendX * t * t);
            int cz = origin.getZ() + (int) Math.round(bendZ * t * t);
            double radius = Math.max(0.75, baseRadius * (1.0 - t) + 0.15);
            for (int dx = -baseRadius; dx <= baseRadius; dx++) {
                for (int dz = -baseRadius; dz <= baseRadius; dz++) {
                    if (dx * dx + dz * dz > radius * radius) continue;
                    BlockPos pos = new BlockPos(cx + dx, origin.getY() + y, cz + dz);
                    if (pos.getY() >= 315 || pos.getY() < -60) continue;
                    BlockState state;
                    if (dx == 0 && dz == 0 && (y % 5 == 0 || y > height - 5)) {
                        state = LumenwildMod.HALO_CRYSTAL.getDefaultState();
                    } else if (random.nextInt(5) == 0) {
                        state = LumenwildMod.PRISM_BLOCK.getDefaultState();
                    } else {
                        state = LumenwildMod.CRYSTAL_BLOOM.getDefaultState();
                    }
                    world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
                }
            }
        }

        // Branches produce an unmistakable silhouette from far away.
        int arms = 2 + random.nextInt(3);
        for (int arm = 0; arm < arms; arm++) {
            double angle = (Math.PI * 2 * arm) / arms + random.nextFloat();
            int start = height / 3 + random.nextInt(Math.max(2, height / 4));
            int length = 5 + random.nextInt(9);
            for (int step = 0; step < length; step++) {
                int ox = (int) Math.round(Math.cos(angle) * step * 0.75);
                int oz = (int) Math.round(Math.sin(angle) * step * 0.75);
                BlockPos pos = origin.add(ox, start + step, oz);
                if (pos.getY() >= 316) break;
                world.setBlockState(pos, (step % 3 == 0
                    ? LumenwildMod.HALO_CRYSTAL
                    : LumenwildMod.CRYSTAL_BLOOM).getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        return true;
    }
}
