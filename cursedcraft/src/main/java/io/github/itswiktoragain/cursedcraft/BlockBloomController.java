package io.github.itswiktoragain.cursedcraft;

import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;
import java.util.Random;

/**
 * Each broken block becomes a rising block display, expands to a
 * 31-block-wide cube at Y=200, then splits into 31 x 31 x 31 falling
 * entities. All operations are batched across server ticks.
 */
public final class BlockBloomController {
    private static final int SIDE = 31;
    private static final int FULL_CUBE = SIDE * SIDE * SIDE;
    private static final Random RANDOM = new Random();
    private static final Queue<Bloom> BLOOMS = new ArrayDeque<>();

    private BlockBloomController() {}

    public static void onBreak(ServerWorld world, BlockPos pos, BlockState state) {
        if (!world.getServer().isSingleplayer() || state.isAir() || BLOOMS.size() >= 2) return;
        DisplayEntity.BlockDisplayEntity block = EntityType.BLOCK_DISPLAY.create(world, SpawnReason.EVENT);
        if (block == null) return;
        block.setBlockState(state);
        block.setNoGravity(true);
        block.setInterpolationDuration(4);
        block.refreshPositionAndAngles(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 0, 0);
        block.setTransformation(transform(1f));
        if (world.spawnEntity(block)) {
            BLOOMS.add(new Bloom(world, state, block, pos));
        }
    }

    private static AffineTransformation transform(float scale) {
        float center = (scale - 1) * -.5f;
        return new AffineTransformation(
                new Vector3f(center, center, center),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf());
    }

    public static void tick(MinecraftServer server) {
        if (!server.isSingleplayer()) return;
        Bloom current = BLOOMS.peek();
        if (current == null) return;
        current.age++;
        double targetY = Math.min(current.world.getTopYInclusive() - 33,
                Math.max(200, current.origin.getY() + 12));
        if (current.age <= 30) {
            double fraction = current.age / 30.0;
            double y = current.origin.getY() + .5 +
                    (targetY - current.origin.getY() - .5) * fraction;
            current.display.refreshPositionAfterTeleport(current.origin.getX() + .5,
                    y, current.origin.getZ() + .5);
            return;
        }
        if (current.age == 31) {
            current.display.setInterpolationDuration(10);
            current.display.setTransformation(transform(SIDE));
            return;
        }
        if (current.age < 45) return;
        if (!current.display.isRemoved()) current.display.discard();

        // Batches prevent a single 29,791-entity allocation on the render thread.
        int spawnBudget = Math.max(32, Math.min(256, Config.INSTANCE.fallingBlocksPerTick));
        while (spawnBudget-- > 0 && current.next < FULL_CUBE) {
            int number = current.next++;
            int dx = number % SIDE - 15;
            int dz = (number / SIDE) % SIDE - 15;
            int dy = number / (SIDE * SIDE);
            double x = current.origin.getX() + .5 + dx;
            double z = current.origin.getZ() + .5 + dz;
            double y = targetY + dy;
            if (current.world.isOutOfHeightLimit((int)y)) continue;
            FallingBlockEntity falling = new FallingBlockEntity(current.world, x, y, z, current.state);
            falling.setNoGravity(true);
            falling.setDestroyedOnLanding();
            if (current.world.spawnEntity(falling)) current.falling.add(falling);
        }
        if (current.next >= FULL_CUBE) {
            int releaseBudget = 400;
            while (releaseBudget-- > 0 && current.releaseIndex < current.falling.size()) {
                FallingBlockEntity falling = current.falling.get(current.releaseIndex++);
                if (!falling.isRemoved()) {
                    falling.setNoGravity(false);
                    falling.setVelocity((RANDOM.nextDouble() - .5) * .22,
                            -.08 - RANDOM.nextDouble() * .2,
                            (RANDOM.nextDouble() - .5) * .22);
                }
            }
            if (current.releaseIndex >= current.falling.size()) BLOOMS.poll();
        }
    }

    public static void reset() {
        BLOOMS.clear();
    }

    private static final class Bloom {
        final ServerWorld world;
        final BlockState state;
        final DisplayEntity.BlockDisplayEntity display;
        final BlockPos origin;
        final ArrayList<FallingBlockEntity> falling = new ArrayList<>(FULL_CUBE);
        int age;
        int next;
        int releaseIndex;

        Bloom(ServerWorld world, BlockState state, DisplayEntity.BlockDisplayEntity display,
              BlockPos origin) {
            this.world = world;
            this.state = state;
            this.display = display;
            this.origin = origin.toImmutable();
        }
    }
}
