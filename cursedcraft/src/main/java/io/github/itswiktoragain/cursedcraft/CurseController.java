package io.github.itswiktoragain.cursedcraft;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class CurseController {
    private static final Map<UUID, Vec3d> LAST_POS = new HashMap<>();
    private static final Map<UUID, Double> DISTANCES = new HashMap<>();
    private static final String[] NAMES = {"Dream", "Technoblade", "MrBeast", "DanTDM", "CaptainSparklez", "TommyInnit", "Grian", "Mumbo"};
    private static final Block[] GLITCH_BLOCKS = {
        Blocks.OBSIDIAN, Blocks.DIAMOND_BLOCK, Blocks.TNT, Blocks.REDSTONE_BLOCK,
        Blocks.GLASS, Blocks.BEDROCK, Blocks.SPONGE, Blocks.MAGMA_BLOCK, Blocks.ICE,
        Blocks.NETHERRACK, Blocks.END_STONE, Blocks.SLIME_BLOCK, Blocks.AMETHYST_BLOCK
    };
    private static final EntityType<?>[] MOBS = {
        EntityType.CREEPER, EntityType.SKELETON, EntityType.ENDERMAN, EntityType.SPIDER,
        EntityType.COW, EntityType.PIG, EntityType.GHAST, EntityType.ZOMBIE
    };
    private static long apocalypseAt = -1L;
    private static UUID victimId;
    private static ServerWorld victimWorld;
    private static Vec3d epicenter;
    private static Path activeSave;
    private static boolean cleanupScheduled;
    private static final Random RANDOM = new Random();

    private CurseController() {}

    public static boolean isActive() { return apocalypseAt > 0; }

    public static void trigger(ServerPlayerEntity player, String why) {
        MinecraftServer server = player.getServer();
        if (isActive() || !server.isSingleplayer()) return;
        victimId = player.getUuid();
        victimWorld = player.getServerWorld();
        epicenter = player.getPos();
        activeSave = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        AlternateSeedCorruption.reset();
        apocalypseAt = System.nanoTime() + 5_000_000_000L;
        player.sendMessage(Text.literal("§4§lCURSEDCRAFT §cThe world is unraveling. 5 seconds."), true);
        CursedCraft.LOGGER.warn("CursedCraft {} apocalypse in {} (delete={})", why, activeSave,
                Config.INSTANCE.deleteCurrentSingleplayerWorld);
    }

    public static void tick(MinecraftServer server) {
        if (!server.isSingleplayer()) return;
        if (isActive()) {
            if (System.nanoTime() >= apocalypseAt) {
                if (!cleanupScheduled) {
                    cleanupScheduled = true;
                    Path save = activeSave;
                    boolean delete = Config.INSTANCE.deleteCurrentSingleplayerWorld;
                    server.stop(false);
                    new Thread(() -> {
                        try {
                            while (!server.isStopped()) Thread.sleep(100);
                            if (delete && save != null && Files.isDirectory(save)
                                    && save.getFileName() != null
                                    && !save.getFileName().toString().equals("saves")) {
                                try (var walk = Files.walk(save)) {
                                    for (Path path : walk.sorted(Comparator.reverseOrder()).toList())
                                        Files.deleteIfExists(path);
                                }
                            }
                        } catch (Exception ex) {
                            CursedCraft.LOGGER.error("Could not finish apocalypse cleanup", ex);
                        }
                        // Shut down the game after the server finishes and save files are closed.
                        Runtime.getRuntime().halt(73);
                    }, "CursedCraft-world-cleanup").start();
                }
                return;
            }
            AlternateSeedCorruption.tick(victimWorld, epicenter, Config.INSTANCE.corruptionRadiusBlocks);
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();
            Vec3d now = player.getPos();
            Vec3d previous = LAST_POS.put(id, now);
            if (previous != null && player.isAlive()) {
                double dx = now.x - previous.x;
                double dz = now.z - previous.z;
                double step = Math.sqrt(dx * dx + dz * dz);
                if (step < 3 && step > 0.001 && player.isOnGround()) {
                    double total = DISTANCES.merge(id, step, Double::sum);
                    float scale = (float) Math.max(0, 1 - total / Config.INSTANCE.walkBlocksUntilLegsGone);
                    ((LegProgress) player).cursedcraft$setLegScale(scale);
                    if (scale <= 0) { trigger(player, "legs"); return; }
                }
            }
            if (Config.INSTANCE.maxMannequinsNearby > 0
                    && server.getTicks() % Config.INSTANCE.mannequinRollIntervalTicks == 0
                    && RANDOM.nextDouble() < Config.INSTANCE.mannequinSpawnChance) {
                trySpawn(player);
            }
        }
    }

    private static void trySpawn(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        var nearby = world.getEntitiesByClass(MannequinEntity.class,
                player.getBoundingBox().expand(96), mob -> true);
        if (nearby.size() >= Config.INSTANCE.maxMannequinsNearby) return;
        double a = RANDOM.nextDouble() * Math.PI * 2;
        int x = player.getBlockX() + (int)(Math.cos(a) * (14 + RANDOM.nextInt(20)));
        int z = player.getBlockZ() + (int)(Math.sin(a) * (14 + RANDOM.nextInt(20)));
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
        int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        MannequinEntity mob = CursedCraft.MANNEQUIN.create(world, SpawnReason.EVENT);
        if (mob == null) return;
        mob.refreshPositionAndAngles(x + .5, y, z + .5, RANDOM.nextFloat() * 360, 0);
        mob.setIdentity(NAMES[RANDOM.nextInt(NAMES.length)], false);
        mob.setTarget(player);
        world.spawnEntity(mob);
    }

    public static void spawnPlayerCopy(ServerWorld world, ServerPlayerEntity player, MannequinEntity source) {
        MannequinEntity copy = CursedCraft.MANNEQUIN.create(world, SpawnReason.EVENT);
        if (copy == null) return;
        copy.refreshPositionAndAngles(source.getX(), source.getY() + 0.7, source.getZ(), 0, 0);
        copy.setIdentity(player.getGameProfile().getName(), true);
        copy.setVelocity((RANDOM.nextDouble() - .5) * 1.2, .65, (RANDOM.nextDouble() - .5) * 1.2);
        copy.setTarget(player);
        world.spawnEntity(copy);
    }

    private static void corrupt(ServerWorld world, Vec3d origin) {
        if (world == null || origin == null) return;
        int radius = Config.INSTANCE.corruptionRadiusBlocks;
        int n = Config.INSTANCE.corruptionPatchesPerTick;
        long salt = RANDOM.nextLong();
        for (int i = 0; i < n; i++) {
            int dx = RANDOM.nextInt(radius * 2 + 1) - radius;
            int dz = RANDOM.nextInt(radius * 2 + 1) - radius;
            if (dx * (long)dx + dz * (long)dz > radius * (long)radius) continue;
            int x = (int)origin.x + dx, z = (int)origin.z + dz;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            int top = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            // Miniature foreign-seed terrain patches: deterministic patterns with fresh seeds.
            Random patch = new Random(salt ^ ((long)x * 341873128712L) ^ ((long)z * 132897987541L));
            Block block = GLITCH_BLOCKS[patch.nextInt(GLITCH_BLOCKS.length)];
            int radiusPatch = 1 + patch.nextInt(4);
            for (int xx = -radiusPatch; xx <= radiusPatch; xx++) {
                for (int zz = -radiusPatch; zz <= radiusPatch; zz++) {
                    if (!world.isChunkLoaded((x + xx) >> 4, (z + zz) >> 4)) continue;
                    int height = 1 + patch.nextInt(5);
                    for (int yy = 0; yy < height; yy++) {
                        BlockPos pos = new BlockPos(x + xx, Math.min(world.getTopYInclusive(), top + yy), z + zz);
                        if (pos.getY() >= world.getBottomY())
                            world.setBlockState(pos, block.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
            }
            if (RANDOM.nextInt(3) == 0) {
                EntityType<?> type = MOBS[RANDOM.nextInt(MOBS.length)];
                var entity = type.create(world, SpawnReason.EVENT);
                if (entity != null) {
                    entity.refreshPositionAndAngles(x + .5, top + 3, z + .5, 0, 0);
                    world.spawnEntity(entity);
                }
            }
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        if (!cleanupScheduled) {
            AlternateSeedCorruption.reset();
            LAST_POS.clear();
            DISTANCES.clear();
            apocalypseAt = -1L;
            victimId = null;
            victimWorld = null;
            epicenter = null;
            activeSave = null;
        }
    }
}
