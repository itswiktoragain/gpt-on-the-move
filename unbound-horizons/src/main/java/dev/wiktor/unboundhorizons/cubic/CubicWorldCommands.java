package dev.wiktor.unboundhorizons.cubic;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.wiktor.unboundhorizons.vertical.VerticalWorldBorder;
import dev.wiktor.unboundhorizons.vertical.WideBlockPos;
import java.nio.file.Path;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

/**
 * All commands below operate on VIRTUAL 16x16x16 cubic chunks, NOT native
 * Minecraft ServerWorld blocks. Clear user-facing labels prevent confusion.
 */
public final class CubicWorldCommands {
    private CubicWorldCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = CommandManager.literal("cubicworld")
                .requires(source -> source.hasPermissionLevel(2));
            root.then(CommandManager.literal("info").executes(ctx -> {
                ctx.getSource().sendFeedback(() -> Text.literal(
                    "VIRTUAL cubic generator: 16x16x16 3D chunks; Y=-29,999,984..+29,999,984. "
                    + "Caves, ores, geology biomes and water/lava lakes at extreme Y. "
                    + "ACTUAL playable Minecraft is still Y=-4096..+4095."), false);
                return 1;
            }));
            for (String name : new String[]{"get", "biome", "chunk", "mine", "restore"}) {
                root.then(CommandManager.literal(name).then(positionArguments(ctx -> inspect(ctx,name))));
            }
            root.then(CommandManager.literal("set")
                .then(CommandManager.argument("x", IntegerArgumentType.integer(-30_000_000,30_000_000))
                    .then(CommandManager.argument("y", IntegerArgumentType.integer(
                        VerticalWorldBorder.MIN_PLAYABLE_Y, VerticalWorldBorder.MAX_PLAYABLE_Y))
                        .then(CommandManager.argument("z", IntegerArgumentType.integer(-30_000_000,30_000_000))
                            .then(CommandManager.argument("block", StringArgumentType.word())
                                .executes(ctx -> inspect(ctx,"set")))))));
            dispatcher.register(root);
        });
    }

    private static RequiredArgumentBuilder<ServerCommandSource,Integer> positionArguments(
        Command<ServerCommandSource> execute) {
        return CommandManager.argument("x", IntegerArgumentType.integer(-30_000_000,30_000_000))
            .then(CommandManager.argument("y", IntegerArgumentType.integer(
                VerticalWorldBorder.MIN_PLAYABLE_Y, VerticalWorldBorder.MAX_PLAYABLE_Y))
                .then(CommandManager.argument("z", IntegerArgumentType.integer(-30_000_000,30_000_000))
                    .executes(execute)));
    }

    private static int inspect(CommandContext<ServerCommandSource> ctx,String action) {
        ServerCommandSource source=ctx.getSource();
        WideBlockPos pos=new WideBlockPos(
            IntegerArgumentType.getInteger(ctx,"x"),
            IntegerArgumentType.getInteger(ctx,"y"),
            IntegerArgumentType.getInteger(ctx,"z"));
        try (CubicTerrainWorld world=new CubicTerrainWorld(root(source),source.getWorld().getSeed())) {
            String reply;
            switch (action) {
                case "get" -> reply="VIRTUAL cubic block " + pos + " = " + world.get(pos)
                    + " (natural: " + world.getNatural(pos) + ")";
                case "biome" -> reply="VIRTUAL 3D biome at " + pos + " = "
                    + world.biomeAt(pos).id();
                case "chunk" -> {
                    CubicChunk cube=world.getChunk(pos.sectionX(),pos.sectionY(),pos.sectionZ());
                    reply="VIRTUAL 16x16x16 chunk X=" + cube.x() + " Y=" + cube.y()
                        + " Z=" + cube.z() + ", biome=" + cube.biome().id()
                        + ", ores=" + cube.oreCount()
                        + ", water=" + cube.count(CubicBlocks.WATER)
                        + ", lava=" + cube.count(CubicBlocks.LAVA)
                        + ", air=" + cube.count(CubicBlocks.AIR);
                }
                case "mine" -> {
                    world.mine(pos);
                    reply="Saved VIRTUAL mined air block at " + pos
                        + " (does NOT alter Minecraft's visible world)";
                }
                case "restore" -> {
                    world.restoreNatural(pos);
                    reply="Restored VIRTUAL natural terrain at " + pos;
                }
                case "set" -> {
                    String block=StringArgumentType.getString(ctx,"block");
                    world.set(pos,block);
                    reply="Saved VIRTUAL block " + block + " at " + pos
                        + " (does NOT alter Minecraft's visible world)";
                }
                default -> { return 0; }
            }
            final String feedback=reply;
            source.sendFeedback(() -> Text.literal(feedback),false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            source.sendError(Text.literal("Cubic virtual-world command failed: " + error));
            return 0;
        }
    }

    private static Path root(ServerCommandSource source) {
        String dimension=source.getWorld().getRegistryKey().getValue().toString()
            .replaceAll("[^a-z0-9_-]","_");
        return source.getServer().getSavePath(WorldSavePath.ROOT)
            .resolve("unboundhorizons").resolve("cubic_world").resolve(dimension);
    }
}
