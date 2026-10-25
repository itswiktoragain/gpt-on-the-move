package io.github.itswiktoragain.cursedcraft;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.border.WorldBorder;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** All boats become cursed launch vehicles, ending at the world border. */
public final class BorderChaosController {
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final Queue<FarlandsTask> FARLANDS = new ArrayDeque<>();
    private static final int MAX_WORLD_COORD = 29999800;

    private BorderChaosController() {}

    public static void tick(MinecraftServer server) {
        if (!server.isSingleplayer()) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isAlive()) continue;
            Flight flight = FLIGHTS.get(player.getUuid());
            if (flight != null) {
                progress(flight);
            } else if (player.getVehicle() instanceof AbstractBoatEntity boat) {
                if (server.getTicks() > COOLDOWNS.getOrDefault(player.getUuid(), -1L)) {
                    start(player, boat);
                }
            } else if (RANDOM.nextDouble() < Config.INSTANCE.randomBorderTeleportChance) {
                teleportToBorder(player);
            }
        }
        FarlandsTask task = FARLANDS.peek();
        if (task != null && task.tick()) FARLANDS.poll();
    }

    public static void forceBoat(ServerPlayerEntity player) {
        if (!player.getServer().isSingleplayer() || FLIGHTS.containsKey(player.getUuid())) return;
        ServerWorld world = player.getServerWorld();
        var boat = EntityType.OAK_BOAT.create(world, SpawnReason.EVENT);
        if (boat == null) return;
        boat.refreshPositionAndAngles(player.getX(), player.getY() + .2, player.getZ(), 0, 0);
        if (!world.spawnEntity(boat)) return;
        player.startRiding(boat, true);
        start(player, (AbstractBoatEntity)boat);
    }

    private static void start(ServerPlayerEntity player, AbstractBoatEntity boat) {
        if (FLIGHTS.containsKey(player.getUuid())) return;
        Destination end = destination(player.getServerWorld(), player.getX(), player.getZ());
        FLIGHTS.put(player.getUuid(), new Flight(player, boat, end));
        boat.setNoGravity(true);
        player.sendMessage(Text.literal("§4THE BOAT HAS AWAKENED"), true);
    }

    private static Destination destination(ServerWorld world, double x, double z) {
        WorldBorder border = world.getWorldBorder();
        double east = border.getBoundEast() - 100;
        double west = border.getBoundWest() + 100;
        double south = border.getBoundSouth() - 100;
        double north = border.getBoundNorth() + 100;
        // Select a face of the actual border, never outside the build limits.
        double[] distances = {Math.abs(east - x), Math.abs(west - x),
                Math.abs(south - z), Math.abs(north - z)};
        int face = 0;
        for (int i = 1; i < 4; i++) {
            if (distances[i] < distances[face]) face = i;
        }
        double xx = face == 0 ? east : face == 1 ? west : x;
        double zz = face == 2 ? south : face == 3 ? north : z;
        xx = Math.max(-MAX_WORLD_COORD, Math.min(MAX_WORLD_COORD, xx));
        zz = Math.max(-MAX_WORLD_COORD, Math.min(MAX_WORLD_COORD, zz));
        return new Destination((int)Math.floor(xx), (int)Math.floor(zz), face);
    }

    private static void progress(Flight flight) {
        ServerPlayerEntity player = flight.player;
        AbstractBoatEntity boat = flight.boat;
        if (player.isRemoved() || boat.isRemoved()) {
            FLIGHTS.remove(player.getUuid());
            return;
        }
        flight.ticks++;
        if (flight.ticks <= 20) {
            // Accelerate straight upwards, reaching Y=200 before the warp.
            double fraction = flight.ticks / 20.0;
            double y = flight.startY + (Math.max(200, flight.startY + 1) - flight.startY) * fraction;
            boat.setVelocity(0, 0, 0);
            boat.refreshPositionAfterTeleport(flight.startX, y, flight.startZ);
        } else if (flight.ticks == 21) {
            // Supersonic warp: no attempt to tick millions of intermediate chunks.
            ServerWorld world = player.getServerWorld();
            int tx = flight.destination.x;
            int tz = flight.destination.z;
            world.getChunk(tx >> 4, tz >> 4);
            player.stopRiding();
            boat.teleport(world, tx + .5, 200, tz + .5, Set.of(), 0, 0, true);
            player.teleport(world, tx + .5, 201, tz + .5, Set.of(), 0, 0, true);
            player.startRiding(boat, true);
            boat.setNoGravity(true);
        } else if (flight.ticks >= 30) {
            ServerWorld world = player.getServerWorld();
            int x = flight.destination.x;
            int z = flight.destination.z;
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (world.isOutOfHeightLimit(y + 4)) y = Math.max(world.getBottomY() + 4, 125);
            player.stopRiding();
            player.teleport(world, x + .5, y + 5, z + .5, Set.of(), 0, 0, true);
            boat.discard();
            buildWitchHut(world, x, y, z);
            FARLANDS.add(new FarlandsTask(world, flight.destination));
            COOLDOWNS.put(player.getUuid(), world.getServer().getTicks() + 200L);
            FLIGHTS.remove(player.getUuid());
            player.sendMessage(Text.literal("§4THE FAR LANDS ARE HERE"), true);
        }
    }

    public static void teleportToBorder(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Destination dest = destination(world, player.getX(), player.getZ());
        world.getChunk(dest.x >> 4, dest.z >> 4);
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, dest.x, dest.z);
        player.teleport(world, dest.x + .5, y + 7, dest.z + .5, Set.of(),
                player.getYaw(), player.getPitch(), true);
        player.sendMessage(Text.literal("§5[POSITION CORRUPTED] §oWorld border reached."), true);
    }

    private static void put(ServerWorld world, int x, int y, int z, BlockState block) {
        if (!world.isOutOfHeightLimit(y))
            world.setBlockState(new BlockPos(x, y, z), block, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
    }

    private static void buildWitchHut(ServerWorld world, int x, int y, int z) {
        // Place the actual vanilla structure when the structure registry supports it.
        try {
            String cmd = "place structure minecraft:swamp_hut " + x + " " + y + " " + z;
            world.getServer().getCommandManager().executeWithPrefix(
                    world.getServer().getCommandSource(), cmd);
        } catch (Exception e) {
            CursedCraft.LOGGER.warn("Vanilla swamp hut placement unavailable; making fallback hut", e);
        }

        // An unmistakable witch-hut silhouette, also serving as a fallback for
        // generators/biomes where /place structure refuses to generate.
        int floor = Math.min(world.getTopYInclusive() - 12, y + 3);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            put(world, x + dx, floor, z + dz, Blocks.SPRUCE_PLANKS.getDefaultState());
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (dx == -3 || dx == 3) {
                    put(world, x + dx, floor + dy, z - 3, Blocks.SPRUCE_PLANKS.getDefaultState());
                    put(world, x + dx, floor + dy, z + 3, Blocks.SPRUCE_PLANKS.getDefaultState());
                }
            }
            for (int dz = -2; dz <= 2; dz++) {
                put(world, x - 3, floor + dy, z + dz, Blocks.SPRUCE_PLANKS.getDefaultState());
                put(world, x + 3, floor + dy, z + dz, Blocks.SPRUCE_PLANKS.getDefaultState());
            }
        }
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++)
            put(world, x + dx, floor + 4, z + dz, Blocks.SPRUCE_SLAB.getDefaultState());
        for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3})
            for (int dy = -3; dy <= 3; dy++)
                put(world, x + dx, floor + dy, z + dz, Blocks.OAK_LOG.getDefaultState());
        put(world, x, floor + 1, z, Blocks.CAULDRON.getDefaultState());
        put(world, x, floor + 1, z + 1, Blocks.CRAFTING_TABLE.getDefaultState());
        put(world, x + 3, floor + 1, z, Blocks.AIR.getDefaultState());
        put(world, x + 3, floor + 2, z, Blocks.AIR.getDefaultState());

        var witch = EntityType.WITCH.create(world, SpawnReason.EVENT);
        if (witch != null) {
            witch.refreshPositionAndAngles(x + .5, floor + 1, z - .5, 0, 0);
            world.spawnEntity(witch);
        }
    }

    public static void reset() {
        FLIGHTS.clear();
        COOLDOWNS.clear();
        FARLANDS.clear();
    }

    private record Destination(int x, int z, int face) {}

    private static final class Flight {
        final ServerPlayerEntity player;
        final AbstractBoatEntity boat;
        final Destination destination;
        final double startX;
        final double startY;
        final double startZ;
        int ticks;

        Flight(ServerPlayerEntity player, AbstractBoatEntity boat, Destination end) {
            this.player = player;
            this.boat = boat;
            destination = end;
            startX = boat.getX();
            startY = boat.getY();
            startZ = boat.getZ();
        }
    }

    private static final class FarlandsTask {
        final ServerWorld world;
        final Destination target;
        int column;

        FarlandsTask(ServerWorld world, Destination target) {
            this.world = world;
            this.target = target;
        }

        boolean tick() {
            int work = 24;
            final int width = 80; // 80x80 positions near the world border.
            WorldBorder border = world.getWorldBorder();
            while (work-- > 0 && column < width * width) {
                int x = target.x + (column % width) - width / 2;
                int z = target.z + (column / width) - width / 2;
                column++;
                if (Math.abs(x) > MAX_WORLD_COORD || Math.abs(z) > MAX_WORLD_COORD) continue;
                double distance = Math.min(
                        Math.min(border.getBoundEast() - x, x - border.getBoundWest()),
                        Math.min(border.getBoundSouth() - z, z - border.getBoundNorth()));
                if (distance < 2 || distance > 100) continue;

                world.getChunk(x >> 4, z >> 4);
                int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                long n = ((long)x * 341873128712L) ^ ((long)z * 132897987541L);
                n ^= n >>> 29;
                // The 1.21.4 generator no longer has the original Far Lands
                // integer-overflow bug. This intentionally recreates its
                // sheer broken walls, honeycomb voids, and 3D cliff noise.
                int height = 65 + (int)(Math.abs(n % 90));
                int top = Math.min(world.getTopYInclusive() - 1, Math.max(ground + 1, height + 96));
                if ((n & 15L) == 0) top = Math.min(world.getTopYInclusive() - 1, top + 55);
                boolean gap = (n & 31L) < 7;
                for (int y = Math.max(ground, world.getBottomY()); y <= top; y++) {
                    boolean hole = ((x >> 2) ^ (z >> 3) ^ (y >> 3)) % 11 == 0;
                    if (gap && (y & 15) < 10) hole = true;
                    BlockState state = hole ? Blocks.AIR.getDefaultState()
                            : ((n + y) % 13 == 0 ? Blocks.DEEPSLATE : Blocks.STONE).getDefaultState();
                    put(world, x, y, z, state);
                }
            }
            return column >= width * width;
        }
    }
}
