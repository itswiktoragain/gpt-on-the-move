package io.github.itswiktoragain.cursedcraft;

import io.github.itswiktoragain.cursedcraft.mixin.VerticalBlockSampleAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.registry.RegistryKeys;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Swaps complete vertical columns in 16x16 chunk footprints with columns
 * sampled from Mojang's real NoiseChunkGenerator configured with another seed.
 * Chunk loading is deliberately forced, so this also generates unseen chunks.
 */
public final class AlternateSeedCorruption {
    private static final Random RANDOM = new Random();
    private static final Set<Long> SEEN = new HashSet<>();
    private static Transfer transfer;
    private static int completed;

    private static final Block[] DEBRIS = {
            Blocks.BEDROCK, Blocks.GLASS, Blocks.TNT, Blocks.OBSIDIAN,
            Blocks.MAGMA_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.REDSTONE_BLOCK
    };
    private static final EntityType<?>[] MOBS = {
            EntityType.CREEPER, EntityType.ENDERMAN, EntityType.ZOMBIE,
            EntityType.SKELETON, EntityType.SPIDER, EntityType.COW,
            EntityType.PIG, EntityType.GHAST
    };

    private AlternateSeedCorruption() {}

    public static void reset() {
        transfer = null;
        SEEN.clear();
        completed = 0;
    }

    public static void tick(ServerWorld world, Vec3d epicenter, int range) {
        if (world == null || epicenter == null) return;
        ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
        if (!(generator instanceof NoiseChunkGenerator noise)) {
            scatter(world, epicenter, range);
            return;
        }
        if (transfer == null) {
            if (completed >= 25) {
                scatter(world, epicenter, range);
                return;
            }
            int effective = RANDOM.nextInt(4) == 0 ? range : Math.min(range, 96);
            int cx = ((int)Math.floor(epicenter.x)) >> 4;
            int cz = ((int)Math.floor(epicenter.z)) >> 4;
            int dx = RANDOM.nextInt((effective / 16) * 2 + 1) - effective / 16;
            int dz = RANDOM.nextInt((effective / 16) * 2 + 1) - effective / 16;
            long chunkKey = (((long)(cx + dx)) << 32) ^ (cz + dz & 0xFFFFFFFFL);
            if (!SEEN.add(chunkKey)) return;
            // The regular overworld generator is still enabled. Generate missing
            // chunks normally before replacing their terrain with the other seed.
            world.getChunk(cx + dx, cz + dz);
            long alternateSeed = RANDOM.nextLong();
            NoiseConfig alternateNoise = NoiseConfig.create(
                    noise.getSettings().value(),
                    world.getRegistryManager().get(RegistryKeys.NOISE_PARAMETERS),
                    alternateSeed);
            transfer = new Transfer(cx + dx, cz + dz, alternateSeed, alternateNoise);
            CursedCraft.LOGGER.info("Other-seed terrain: chunk {},{} from seed {}",
                    transfer.chunkX, transfer.chunkZ, alternateSeed);
        }

        // Do several columns per tick to keep the integrated server responsive.
        final int columnsThisTick = 8;
        for (int i = 0; i < columnsThisTick && transfer != null; i++) {
            int col = transfer.nextColumn++;
            int x = (transfer.chunkX << 4) + (col & 15);
            int z = (transfer.chunkZ << 4) + (col >> 4);

            try {
                // This is actual vanilla seed-dependent terrain noise, including
                // air caves, stone, solid blocks and liquid-filled cavities.
                VerticalBlockSample sample = noise.getColumnSample(x, z, world, transfer.noiseConfig);
                VerticalBlockSampleAccessor access = (VerticalBlockSampleAccessor) (Object) sample;
                BlockState[] columnStates = access.cursedcraft$getStates();
                int startY = access.cursedcraft$getStartY();
                BlockPos.Mutable mutable = new BlockPos.Mutable();
                for (int n = 0; n < columnStates.length; n++) {
                    int y = startY + n;
                    if (world.isOutOfHeightLimit(y)) continue;
                    BlockState alt = columnStates[n];
                    mutable.set(x, y, z);
                    if (!world.getBlockState(mutable).equals(alt)) {
                        // Send individual changes to clients, but avoid scheduling
                        // expensive recursive redstone neighbor updates.
                        world.setBlockState(mutable, alt, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                    }
                }
            } catch (Exception e) {
                CursedCraft.LOGGER.warn("Alternate-seed generation failed for one column", e);
            }
            if (transfer.nextColumn >= 256) {
                completed++;
                scatter(world, new Vec3d((transfer.chunkX << 4) + 8,
                        epicenter.y, (transfer.chunkZ << 4) + 8), 18);
                transfer = null;
            }
        }
        scatter(world, epicenter, Math.min(range, 65));
    }

    private static void scatter(ServerWorld world, Vec3d epicenter, int range) {
        int x = (int)Math.floor(epicenter.x) + RANDOM.nextInt(range * 2 + 1) - range;
        int z = (int)Math.floor(epicenter.z) + RANDOM.nextInt(range * 2 + 1) - range;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
        int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (world.isOutOfHeightLimit(y)) return;
        if (RANDOM.nextInt(3) == 0) {
            world.setBlockState(new BlockPos(x, y, z),
                    DEBRIS[RANDOM.nextInt(DEBRIS.length)].getDefaultState(),
                    Block.NOTIFY_LISTENERS);
        }
        if (RANDOM.nextInt(8) == 0) {
            var entity = MOBS[RANDOM.nextInt(MOBS.length)].create(world, SpawnReason.EVENT);
            if (entity != null) {
                entity.refreshPositionAndAngles(x + .5, y + 2, z + .5, 0, 0);
                world.spawnEntity(entity);
            }
        }
    }

    private static final class Transfer {
        final int chunkX;
        final int chunkZ;
        final long seed;
        final NoiseConfig noiseConfig;
        int nextColumn;

        Transfer(int chunkX, int chunkZ, long seed, NoiseConfig noiseConfig) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.seed = seed;
            this.noiseConfig = noiseConfig;
        }
    }
}
