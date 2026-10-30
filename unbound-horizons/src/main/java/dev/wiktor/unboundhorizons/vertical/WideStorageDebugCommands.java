package dev.wiktor.unboundhorizons.vertical;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

/**
 * Developer diagnostics for the independent sparse section backend.
 *
 * The commands deliberately say "VIRTUAL" because the renderer, vanilla
 * gameplay physics and native world chunks are NOT wired to this storage.
 * No commands here alter regular Minecraft block states.
 */
public final class WideStorageDebugCommands {
    private WideStorageDebugCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("unboundheight")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("info").executes(context -> {
                    context.getSource().sendFeedback(() -> Text.literal(
                        "Unbound Horizons experimental backend: virtual Y " +
                        "-30,000,000..+30,000,000. Regular playable world remains " +
                        "-4096..+4095. This is NOT yet real rendered world height."
                    ), false);
                    return 1;
                }))
                .then(CommandManager.literal("get")
                    .then(CommandManager.argument("x", IntegerArgumentType.integer(-30_000_000,30_000_000))
                        .then(CommandManager.argument("y", IntegerArgumentType.integer(-30_000_000,30_000_000))
                            .then(CommandManager.argument("z", IntegerArgumentType.integer(-30_000_000,30_000_000))
                                .executes(ctx -> {
                                    ServerCommandSource source = ctx.getSource();
                                    WideBlockPos pos = coordinates(ctx);
                                    try (SparseVerticalStore store = new SparseVerticalStore(rootFor(source))) {
                                        String id = store.get(pos);
                                        source.sendFeedback(() -> Text.literal(
                                            "VIRTUAL ONLY: " + pos + " -> " + id), false);
                                        return 1;
                                    } catch (IOException error) {
                                        source.sendError(Text.literal("Virtual storage read failed: " + error));
                                        return 0;
                                    }
                                })))))
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("x", IntegerArgumentType.integer(-30_000_000,30_000_000))
                        .then(CommandManager.argument("y", IntegerArgumentType.integer(-30_000_000,30_000_000))
                            .then(CommandManager.argument("z", IntegerArgumentType.integer(-30_000_000,30_000_000))
                                .then(CommandManager.argument("block", StringArgumentType.word())
                                    .executes(ctx -> {
                                        ServerCommandSource source = ctx.getSource();
                                        WideBlockPos pos = coordinates(ctx);
                                        String block = StringArgumentType.getString(ctx, "block");
                                        try (SparseVerticalStore store = new SparseVerticalStore(rootFor(source))) {
                                            store.set(pos, block);
                                            source.sendFeedback(() -> Text.literal(
                                                "Saved VIRTUAL block at " + pos + ": " + block
                                                + " (not placed in the playable world)"), false);
                                            return 1;
                                        } catch (IOException | IllegalArgumentException error) {
                                            source.sendError(Text.literal("Virtual storage write failed: " + error));
                                            return 0;
                                        }
                                    })))))));
        });
    }

    private static WideBlockPos coordinates(
        com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        return new WideBlockPos(
            IntegerArgumentType.getInteger(ctx,"x"),
            IntegerArgumentType.getInteger(ctx,"y"),
            IntegerArgumentType.getInteger(ctx,"z"));
    }

    private static Path rootFor(ServerCommandSource source) {
        String dimension = source.getWorld().getRegistryKey().getValue()
            .toString().replaceAll("[^a-z0-9_-]", "_");
        return source.getServer().getSavePath(WorldSavePath.ROOT)
            .resolve("unboundhorizons").resolve("wide_sections").resolve(dimension);
    }
}
