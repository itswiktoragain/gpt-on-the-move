package dev.bullet;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class BulletMod implements ModInitializer {
    public static final String MOD_ID = "bullet";

    public static final Identifier ENTITY_ID = Identifier.of(MOD_ID, "bullet");
    public static final RegistryKey<EntityType<?>> ENTITY_KEY =
        RegistryKey.of(RegistryKeys.ENTITY_TYPE, ENTITY_ID);

    public static final EntityType<BulletEntity> BULLET_ENTITY = Registry.register(
        Registries.ENTITY_TYPE, ENTITY_ID,
        EntityType.Builder.<BulletEntity>create(BulletEntity::new, SpawnGroup.MISC)
            .dimensions(1.45f, 0.75f)
            .maxTrackingRange(12)
            .trackingTickInterval(1)
            .build(ENTITY_KEY)
    );

    public static final Identifier ITEM_ID = Identifier.of(MOD_ID, "bullet");
    public static final RegistryKey<Item> ITEM_KEY =
        RegistryKey.of(RegistryKeys.ITEM, ITEM_ID);

    public static final Item BULLET_ITEM = Registry.register(
        Registries.ITEM, ITEM_ID,
        new BulletItem(new Item.Settings().registryKey(ITEM_KEY).maxCount(1))
    );

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(BULLET_ITEM));

        // The native client screen sends a no-cheats command. Only a mounted passenger
        // can actually use it. This avoids the need for custom packet synchronization.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("bullet_go")
                .then(CommandManager.argument("x", DoubleArgumentType.doubleArg(-29999984, 29999984))
                    .then(CommandManager.argument("y", DoubleArgumentType.doubleArg(-2048, 2048))
                        .then(CommandManager.argument("z", DoubleArgumentType.doubleArg(-29999984, 29999984))
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                if (!(player.getVehicle() instanceof BulletEntity bullet)) {
                                    ctx.getSource().sendError(Text.literal("You must be seated in a Bullet."));
                                    return 0;
                                }
                                double x = DoubleArgumentType.getDouble(ctx, "x");
                                double y = DoubleArgumentType.getDouble(ctx, "y");
                                double z = DoubleArgumentType.getDouble(ctx, "z");
                                if (y < player.getWorld().getBottomY() + 2 || y > player.getWorld().getTopYInclusive() - 2) {
                                    ctx.getSource().sendError(Text.literal("That Y is outside the world's build height."));
                                    return 0;
                                }
                                if (!bullet.setDestination(x, y, z)) {
                                    ctx.getSource().sendError(Text.literal("Bullet is already in flight!"));
                                    return 0;
                                }
                                return 1;
                            })))));
        });
    }
}
