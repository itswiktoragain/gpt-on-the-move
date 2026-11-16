package dev.wiktor.disc;

import java.util.Optional;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.UseCooldown;

public class ThrowingDiscs implements ModInitializer {
    public static final String MODID = "throwingdisc";
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath(MODID, name); }
    public static Item COPPER, IRON, GOLDEN, DIAMOND, NETHERITE;
    public static EntityType<DiscEntity> DISC;
    private static Item register(String name, int damage, double distance, int durability) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key,
            new DiscItem(damage, distance, new Item.Properties()
                .setId(key).stacksTo(1).durability(durability)
                // All five material variants share one player cooldown group.
                // Switching slots, materials, or hands cannot bypass the 10-tick delay.
                .component(DataComponents.USE_COOLDOWN,
                    new UseCooldown(0.5F, Optional.of(id("all_throwing_discs"))))));
    }
    @Override public void onInitialize() {
        COPPER = register("copper_throwing_disc", 4, 24, 175);
        IRON = register("iron_throwing_disc", 5, 29, 350);
        GOLDEN = register("golden_throwing_disc", 4, 27, 110);
        DIAMOND = register("diamond_throwing_disc", 7, 35, 1450);
        NETHERITE = register("netherite_throwing_disc", 8, 40, 2050);
        ResourceKey<EntityType<?>> typeKey = ResourceKey.create(Registries.ENTITY_TYPE, id("throwing_disc"));
        DISC = Registry.register(BuiltInRegistries.ENTITY_TYPE, typeKey,
            EntityType.Builder.<DiscEntity>of(DiscEntity::new, MobCategory.MISC)
             .sized(0.5f, 0.16f).clientTrackingRange(8).updateInterval(1).build(typeKey));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
            output.accept(COPPER); output.accept(IRON); output.accept(GOLDEN);
            output.accept(DIAMOND); output.accept(NETHERITE);
        });
    }
}