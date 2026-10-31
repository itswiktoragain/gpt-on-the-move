package dev.wiktor.lumenwild;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.Set;

public final class LumenwildMod implements ModInitializer {
    public static final String MOD_ID = "lumenwild";

    public static final RegistryKey<World> LUMENWILD = RegistryKey.of(
        RegistryKeys.WORLD, id("lumenwild")
    );

    // Every block also receives a BlockItem.
    public static final Block LUMENSTONE = block("lumenstone",
        new Block(AbstractBlock.Settings.copy(Blocks.STONE).strength(2.2f).luminance(s -> 3).requiresTool()));
    public static final Block DUSKSTONE = block("duskstone",
        new Block(AbstractBlock.Settings.copy(Blocks.DEEPSLATE).strength(3.0f).requiresTool()));
    public static final Block LUMENMOSS = block("lumenmoss",
        new Block(AbstractBlock.Settings.copy(Blocks.MOSS_BLOCK).strength(1.1f).luminance(s -> 7)));
    public static final Block LUMENSTONE_BRICKS = block("lumenstone_bricks",
        new Block(AbstractBlock.Settings.copy(Blocks.STONE_BRICKS).strength(2.5f).luminance(s -> 5).requiresTool()));
    public static final Block PRISM_ORE = block("prism_ore",
        new Block(AbstractBlock.Settings.copy(Blocks.IRON_ORE).strength(3.0f).luminance(s -> 5).requiresTool()));
    public static final Block DEEP_PRISM_ORE = block("deep_prism_ore",
        new Block(AbstractBlock.Settings.copy(Blocks.DEEPSLATE_IRON_ORE).strength(4.5f).luminance(s -> 5).requiresTool()));
    public static final Block UMBRAL_ORE = block("umbral_ore",
        new Block(AbstractBlock.Settings.copy(Blocks.DIAMOND_ORE).strength(4.0f).luminance(s -> 2).requiresTool()));
    public static final Block PRISM_BLOCK = block("prism_block",
        new Block(AbstractBlock.Settings.copy(Blocks.AMETHYST_BLOCK).strength(3.0f).luminance(s -> 11).requiresTool()));
    public static final Block PRISM_LAMP = block("prism_lamp",
        new Block(AbstractBlock.Settings.copy(Blocks.SEA_LANTERN).strength(1.3f).luminance(s -> 15)));


    // These twelve blocks form the geology and construction materials of the sky realm.
    public static final Block AETHERSTONE = block("aetherstone",
        new Block(AbstractBlock.Settings.copy(Blocks.STONE).strength(2.5f).requiresTool().luminance(s -> 2)));
    public static final Block POLISHED_AETHERSTONE = block("polished_aetherstone",
        new Block(AbstractBlock.Settings.copy(Blocks.POLISHED_ANDESITE).strength(2.8f).requiresTool().luminance(s -> 4)));
    public static final Block AETHERSTONE_BRICKS = block("aetherstone_bricks",
        new Block(AbstractBlock.Settings.copy(Blocks.STONE_BRICKS).strength(2.9f).requiresTool().luminance(s -> 4)));
    public static final Block RIFTSTONE = block("riftstone",
        new Block(AbstractBlock.Settings.copy(Blocks.DEEPSLATE).strength(3.4f).requiresTool()));
    public static final Block RIFTSTONE_BRICKS = block("riftstone_bricks",
        new Block(AbstractBlock.Settings.copy(Blocks.DEEPSLATE_BRICKS).strength(3.6f).requiresTool().luminance(s -> 2)));
    public static final Block SHADOW_MOSS = block("shadow_moss",
        new Block(AbstractBlock.Settings.copy(Blocks.MOSS_BLOCK).strength(0.9f).luminance(s -> 4)));
    public static final Block STAR_MOSS = block("star_moss",
        new Block(AbstractBlock.Settings.copy(Blocks.MOSS_BLOCK).strength(0.9f).luminance(s -> 9)));
    public static final Block HALO_CRYSTAL = block("halo_crystal",
        new Block(AbstractBlock.Settings.copy(Blocks.AMETHYST_BLOCK).strength(2.7f).requiresTool().luminance(s -> 15)));
    public static final Block CRYSTAL_BLOOM = block("crystal_bloom",
        new Block(AbstractBlock.Settings.copy(Blocks.AMETHYST_BLOCK).strength(2.3f).requiresTool().luminance(s -> 12)));
    public static final Block WISPWOOD = block("wispwood",
        new Block(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).strength(2.2f).luminance(s -> 3)));
    public static final Block WISP_PLANKS = block("wisp_planks",
        new Block(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).strength(2.0f).luminance(s -> 3)));
    public static final Block SKYGLASS = block("skyglass",
        new Block(AbstractBlock.Settings.copy(Blocks.GLASS).nonOpaque().strength(0.7f).luminance(s -> 7)));

    // Configured / placed features are data-driven, but the geometry is custom Java code.
    public static final net.minecraft.world.gen.feature.Feature<net.minecraft.world.gen.feature.DefaultFeatureConfig> CRYSTAL_SPIRE =
        Registry.register(Registries.FEATURE, id("crystal_spire"),
            new CrystalSpireFeature(net.minecraft.world.gen.feature.DefaultFeatureConfig.CODEC));
    public static final net.minecraft.world.gen.feature.Feature<net.minecraft.world.gen.feature.DefaultFeatureConfig> SKY_ORB =
        Registry.register(Registries.FEATURE, id("sky_orb"),
            new SkyOrbFeature(net.minecraft.world.gen.feature.DefaultFeatureConfig.CODEC));

    public static final Item PRISM_SHARD = item("prism_shard", new Item(new Item.Settings()));
    public static final Item UMBRAL_DUST = item("umbral_dust", new Item(new Item.Settings()));
    public static final Item RIFT_KEY = item("rift_key",
        new Item(new Item.Settings().maxCount(1).fireproof()));

    public static final Item PRISM_SWORD = item("prism_sword",
        new SwordItem(ToolMaterials.DIAMOND,
            new Item.Settings().attributeModifiers(
                SwordItem.createAttributeModifiers(ToolMaterials.DIAMOND, 4, -2.4f)
            )
        )
    );
    public static final Item PRISM_PICKAXE = item("prism_pickaxe",
        new PickaxeItem(ToolMaterials.DIAMOND,
            new Item.Settings().attributeModifiers(
                PickaxeItem.createAttributeModifiers(ToolMaterials.DIAMOND, 2, -2.8f)
            )
        )
    );

    private static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    private static Block block(String name, Block block) {
        Registry.register(Registries.BLOCK, id(name), block);
        Registry.register(Registries.ITEM, id(name), new BlockItem(block, new Item.Settings()));
        return block;
    }

    private static Item item(String name, Item item) {
        return Registry.register(Registries.ITEM, id(name), item);
    }

    @Override
    public void onInitialize() {
        // The test scans generated chunks only when explicitly enabled by CI.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if ("1".equals(System.getenv("LUMENWILD_WORLDGEN_TEST"))) {
                WorldgenSmokeTest.verify(server);
            }
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(e -> {
            e.add(LUMENSTONE);
            e.add(DUSKSTONE);
            e.add(LUMENSTONE_BRICKS);
            e.add(PRISM_BLOCK);
            e.add(PRISM_LAMP);
            e.add(AETHERSTONE);
            e.add(POLISHED_AETHERSTONE);
            e.add(AETHERSTONE_BRICKS);
            e.add(RIFTSTONE);
            e.add(RIFTSTONE_BRICKS);
            e.add(HALO_CRYSTAL);
            e.add(CRYSTAL_BLOOM);
            e.add(WISPWOOD);
            e.add(WISP_PLANKS);
            e.add(SKYGLASS);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(e -> {
            e.add(LUMENMOSS);
            e.add(PRISM_ORE);
            e.add(DEEP_PRISM_ORE);
            e.add(UMBRAL_ORE);
            e.add(SHADOW_MOSS);
            e.add(STAR_MOSS);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(e -> {
            e.add(PRISM_SHARD);
            e.add(UMBRAL_DUST);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> {
            e.add(RIFT_KEY);
            e.add(PRISM_PICKAXE);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> e.add(PRISM_SWORD));

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!player.getStackInHand(hand).isOf(RIFT_KEY)
                    || !world.getBlockState(hit.getBlockPos()).isOf(Blocks.CRYING_OBSIDIAN)) {
                return ActionResult.PASS;
            }
            if (world.isClient) return ActionResult.SUCCESS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (serverPlayer.getItemCooldownManager().isCoolingDown(RIFT_KEY)) return ActionResult.SUCCESS;

            boolean inLumenwild = world.getRegistryKey().equals(LUMENWILD);
            RegistryKey<World> targetKey = inLumenwild ? World.OVERWORLD : LUMENWILD;
            ServerWorld target = serverPlayer.getServer().getWorld(targetKey);
            if (target == null) {
                serverPlayer.sendMessage(Text.literal("Lumenwild is unavailable. Check the mod installation."), true);
                return ActionResult.FAIL;
            }

            int x = serverPlayer.getBlockX();
            int z = serverPlayer.getBlockZ();
            int y = target.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

            if (!inLumenwild && y < 92) {
                // Floating islands have real gaps. Find nearby land if the portal
                // would otherwise lead to an abyss, rather than spawning at Y=-59.
                int[][] offsets = {
                    {24, 0}, {-24, 0}, {0, 24}, {0, -24},
                    {48, 0}, {-48, 0}, {0, 48}, {0, -48},
                    {48, 48}, {-48, 48}, {48, -48}, {-48, -48}
                };
                for (int[] offset : offsets) {
                    int candidateX = x + offset[0];
                    int candidateZ = z + offset[1];
                    int candidateY = target.getTopY(
                        Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, candidateX, candidateZ);
                    if (candidateY >= 105 && candidateY <= 310) {
                        x = candidateX;
                        z = candidateZ;
                        y = candidateY;
                        break;
                    }
                }
                // No nearby island: generate a guaranteed safe sky-platform.
                if (y < 92) y = 166;
            }
            y = Math.max(target.getBottomY() + 5, Math.min(y, target.getTopY() - 5));
            createLandingPad(target, x, y, z, inLumenwild);
            serverPlayer.teleport(target, x + 0.5, y + 0.1, z + 0.5,
                Set.of(), serverPlayer.getYaw(), serverPlayer.getPitch());
            serverPlayer.getItemCooldownManager().set(RIFT_KEY, 40);
            serverPlayer.sendMessage(Text.literal(inLumenwild ? "Returned to the Overworld" : "Entered the Lumenwild"), true);
            return ActionResult.SUCCESS;
        });
    }

    private static void createLandingPad(ServerWorld target, int x, int y, int z, boolean returning) {
        Block ground = returning ? Blocks.STONE_BRICKS : AETHERSTONE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                Block edge = (!returning && (Math.abs(dx) == 3 || Math.abs(dz) == 3))
                    ? LUMENSTONE : ground;
                target.setBlockState(new BlockPos(x + dx, y - 1, z + dz), edge.getDefaultState());
            }
        }
        if (!returning) {
            // Easy-to-see corner lamps make the arrival platform visible in the void.
            for (int dx : new int[]{-3, 3}) {
                for (int dz : new int[]{-3, 3}) {
                    target.setBlockState(new BlockPos(x + dx, y, z + dz),
                        HALO_CRYSTAL.getDefaultState());
                }
            }
        }
        // Prevent suffocation in trees or terrain at the landing spot.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    target.setBlockState(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.getDefaultState());
                }
            }
        }
        // Within arm's reach: right-click again with the key to get home.
        target.setBlockState(new BlockPos(x + 2, y, z), Blocks.CRYING_OBSIDIAN.getDefaultState());
    }
}
