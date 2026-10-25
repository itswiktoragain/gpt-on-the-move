package io.github.itswiktoragain.cursedcraft;

import net.minecraft.entity.SpawnReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Random;

/** Giant player-shaped mannequin with local world corruption and boat trap. */
public final class GiantController {
    private static final Random RANDOM = new Random();

    private GiantController() {}

    public static void tick(MinecraftServer server) {
        if (!server.isSingleplayer() || CurseController.isActive()) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerWorld world = player.getServerWorld();
            List<MannequinEntity> giants = world.getEntitiesByClass(MannequinEntity.class,
                    player.getBoundingBox().expand(160), MannequinEntity::isGiant);

            if (giants.isEmpty()) {
                if (server.getTicks() % Config.INSTANCE.giantCheckIntervalTicks == 0
                        && RANDOM.nextDouble() < Config.INSTANCE.giantSpawnChance) {
                    spawn(world, player);
                }
                continue;
            }
            for (MannequinEntity giant : giants) {
                double dist = giant.getPos().distanceTo(player.getPos());
                if (dist < 9.5 || giant.getBoundingBox().expand(.8).intersects(player.getBoundingBox())) {
                    BorderChaosController.forceBoat(player);
                } else if (dist < 65 && server.getTicks() % 16 == 0) {
                    // Actual terrain changes become more violent as the player approaches.
                    int blasts = dist < 25 ? 3 : dist < 40 ? 2 : 1;
                    for (int k = 0; k < blasts; k++)
                        AlternateSeedCorruption.tick(world, player.getPos(), 30);
                }
            }
        }
    }

    private static void spawn(ServerWorld world, ServerPlayerEntity player) {
        double a = RANDOM.nextDouble() * Math.PI * 2;
        int x = player.getBlockX() + (int)(Math.cos(a) * (42 + RANDOM.nextInt(44)));
        int z = player.getBlockZ() + (int)(Math.sin(a) * (42 + RANDOM.nextInt(44)));
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (world.isOutOfHeightLimit(y + 25)) return;
        MannequinEntity giant = CursedCraft.MANNEQUIN.create(world, SpawnReason.EVENT);
        if (giant == null) return;
        giant.refreshPositionAndAngles(x + .5, y, z + .5, 0, 0);
        giant.setIdentity(player.getGameProfile().getName(), true, player.getUuid().toString());
        giant.setGiant(true);
        world.spawnEntity(giant);
    }
}
