package dev.wiktor.unboundhorizons.cubic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Self-contained deep lake pocket for the native -4096..4095 dimension.
 *
 * Vanilla LakeFeature can inspect/write blocks more than one chunk from the
 * placement origin, and in the submitted 1.21.4 crash this caused a request
 * for a chunk whose generation stage was unavailable. This feature instead
 * constrains all X/Z reads/writes to its OWN 16x16 chunk, no matter which
 * X/Z coordinate the placement modifier chooses inside that chunk.
 *
 * This is deliberately a small flooded cavern (8-10 blocks across), not a
 * full vanilla LakeFeature spanning neighboring chunk columns.
 */
public final class DeepPocketFeature extends Feature<DefaultFeatureConfig> {
    private final BlockState fluid;

    public DeepPocketFeature(BlockState fluid) {
        super(DefaultFeatureConfig.CODEC);
        if (!fluid.isOf(Blocks.WATER) && !fluid.isOf(Blocks.LAVA)) {
            throw new IllegalArgumentException("Only water/lava pockets are supported");
        }
        this.fluid = fluid;
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        var random = context.getRandom();

        int cy = origin.getY();
        // Keep eight vertical blocks of headroom near both dimension limits.
        if (cy < world.getBottomY() + 8
            || cy > world.getTopYInclusive() - 8
            || cy > -128) return false;

        // Always center inside the origin's own chunk. Radius <=5 gives a
        // guaranteed 3-block margin to every neighboring X/Z chunk.
        int cx = Math.floorDiv(origin.getX(), 16) * 16 + 8;
        int cz = Math.floorDiv(origin.getZ(), 16) * 16 + 8;
        int rx = 4 + random.nextInt(2);
        int rz = 4 + random.nextInt(2);
        int ry = 3;
        int changed = 0;
        for (int dy = -ry; dy <= ry; dy++) {
            for (int dz = -rz; dz <= rz; dz++) {
                for (int dx = -rx; dx <= rx; dx++) {
                    double dist = ((double)dx * dx) / (rx * rx)
                        + ((double)dy * dy) / (ry * ry)
                        + ((double)dz * dz) / (rz * rz);
                    if (dist > 1.0) continue;
                    BlockPos at = new BlockPos(cx + dx, cy + dy, cz + dz);
                    BlockState previous = world.getBlockState(at);
                    // Never overwrite structures, player edits, tile entities,
                    // ore veins or other protected blocks.
                    if (!previous.isAir()
                        && !previous.isOf(Blocks.STONE)
                        && !previous.isOf(Blocks.DEEPSLATE)
                        && !previous.isOf(Blocks.TUFF)
                        && !previous.isOf(Blocks.GRANITE)
                        && !previous.isOf(Blocks.ANDESITE)
                        && !previous.isOf(Blocks.DIORITE)
                        && !previous.isOf(Blocks.GRAVEL)) continue;
                    BlockState replacement = dy <= -1
                        ? fluid : Blocks.AIR.getDefaultState();
                    if (replacement.equals(previous)) continue;
                    world.setBlockState(at, replacement, Block.NOTIFY_LISTENERS);
                    changed++;
                }
            }
        }
        return changed > 0;
    }
}
