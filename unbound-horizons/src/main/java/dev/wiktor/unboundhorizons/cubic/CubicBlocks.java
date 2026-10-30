package dev.wiktor.unboundhorizons.cubic;

import java.util.HashMap;
import java.util.Map;

/** Stable, compact per-voxel palette IDs for a 16x16x16 cubic chunk. */
public final class CubicBlocks {
    public static final byte AIR = 0;
    public static final byte STONE = 1;
    public static final byte DEEPSLATE = 2;
    public static final byte TUFF = 3;
    public static final byte BASALT = 4;
    public static final byte BLACKSTONE = 5;
    public static final byte DRIPSTONE = 6;
    public static final byte MOSS = 7;
    public static final byte SCULK = 8;
    public static final byte AMETHYST = 9;
    public static final byte CALCITE = 10;
    public static final byte WATER = 11;
    public static final byte LAVA = 12;
    public static final byte IRON_ORE = 13;
    public static final byte GOLD_ORE = 14;
    public static final byte DIAMOND_ORE = 15;
    public static final byte COAL_ORE = 16;
    public static final byte REDSTONE_ORE = 17;
    public static final byte LAPIS_ORE = 18;
    public static final byte COPPER_ORE = 19;
    public static final byte EMERALD_ORE = 20;
    public static final byte ANCIENT_DEBRIS = 21;
    public static final byte GRASS = 22;
    public static final byte DIRT = 23;
    public static final byte ICE = 24;
    public static final byte CLAY = 25;
    public static final byte OBSIDIAN = 26;

    private static final String[] IDS = {
        "minecraft:air", "minecraft:stone", "minecraft:deepslate",
        "minecraft:tuff", "minecraft:basalt", "minecraft:blackstone",
        "minecraft:dripstone_block", "minecraft:moss_block", "minecraft:sculk",
        "minecraft:amethyst_block", "minecraft:calcite", "minecraft:water",
        "minecraft:lava", "minecraft:deepslate_iron_ore",
        "minecraft:deepslate_gold_ore", "minecraft:deepslate_diamond_ore",
        "minecraft:deepslate_coal_ore", "minecraft:deepslate_redstone_ore",
        "minecraft:deepslate_lapis_ore", "minecraft:deepslate_copper_ore",
        "minecraft:deepslate_emerald_ore", "minecraft:ancient_debris",
        "minecraft:grass_block", "minecraft:dirt", "minecraft:ice",
        "minecraft:clay", "minecraft:obsidian"
    };
    private static final Map<String, Byte> INDEX = new HashMap<>();

    static {
        for (int i = 0; i < IDS.length; i++) INDEX.put(IDS[i], (byte)i);
    }

    private CubicBlocks() {}

    public static String id(byte type) {
        int value = Byte.toUnsignedInt(type);
        if (value >= IDS.length) throw new IllegalArgumentException("Invalid block palette type " + value);
        return IDS[value];
    }

    public static byte type(String id) {
        Byte value = INDEX.get(id);
        if (value == null) throw new IllegalArgumentException("Unsupported natural terrain block ID: " + id);
        return value;
    }

    public static boolean isOre(byte type) {
        int value = Byte.toUnsignedInt(type);
        return value >= IRON_ORE && value <= ANCIENT_DEBRIS;
    }

    public static boolean isFluid(byte type) {
        return type == WATER || type == LAVA;
    }
}
