package io.github.itswiktoragain.cursedcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CursedCraft implements ModInitializer {
    public static final String ID = "cursedcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static final EntityType<MannequinEntity> MANNEQUIN = registerMannequin();

    private static EntityType<MannequinEntity> registerMannequin() {
        Identifier id = Identifier.of(ID, "mannequin");
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);
        return Registry.register(Registries.ENTITY_TYPE, id,
                EntityType.Builder.create(MannequinEntity::new, SpawnGroup.MONSTER)
                        .dimensions(0.6f, 1.95f).maxTrackingRange(10).build(key));
    }

    @Override
    public void onInitialize() {
        Config.load();
        FabricDefaultAttributeRegistry.register(MANNEQUIN, ZombieEntity.createZombieAttributes());
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player && player.getServer().isSingleplayer()) {
                CurseController.trigger(player, "death");
            }
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld serverWorld && serverWorld.getServer().isSingleplayer()) {
                BlockBloomController.onBreak(serverWorld, pos, state);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(CurseController::tick);
        ServerTickEvents.END_SERVER_TICK.register(BlockBloomController::tick);
        ServerTickEvents.END_SERVER_TICK.register(BorderChaosController::tick);
        ServerTickEvents.END_SERVER_TICK.register(GiantController::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CurseController.onServerStopped(server);
            BlockBloomController.reset();
            BorderChaosController.reset();
        });
        LOGGER.warn("CURSEDCRAFT: danger! Use a throwaway Minecraft instance, not one with important worlds.");
    }
}
