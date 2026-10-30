# Lumenwild ✨

A Fabric dimension mod for **Minecraft Java 1.21.1**.

## Features

- The **Lumenwild**: a new teal-skied dimension with unique ambient colors, custom biome and terrain features.
- **Nine blocks:** lumenstone, duskstone, lumenmoss, lumenstone bricks, prism ore, deep prism ore, umbral ore, prism block, and a glowing prism lamp.
- **Custom ore veins**, huge underground luminous stone patches and glowing surface boulders.
- **Prism shards** and **umbral dust**, plus a prism sword, prism pickaxe, and permanent rift key.
- Procedurally generated original pixel-art textures.
- Crafting recipes, loot tables, mining tags, translations, and survival-world compatibility.

## How to enter

1. Find and mine **prism ore** and **umbral ore** (or use Creative mode).
2. Craft a **Rift Key** from prism shards, umbral dust, an ender pearl and crying obsidian.
3. Place **crying obsidian** and right-click it while holding the Rift Key.
4. The key sends you to Lumenwild and builds a small safe landing platform with a crying obsidian return anchor beside it.
5. Right-click the return anchor with the key to return to the Overworld. The key is reusable.

In Creative mode, all additions are in the Building Blocks, Natural Blocks, Combat, Tools, or Ingredients tabs.

## Install

Install Fabric Loader and Fabric API for Minecraft Java 1.21.1, then copy `lumenwild-1.0.0.jar` to your `.minecraft/mods/` folder.

**Backup existing worlds before adding or removing dimensions.** The world must load the mod to retain its dimension.

## Build from source

Requires JDK 21 and Gradle 8.9 (or let GitHub Actions build for you):

```bash
cd lumenwild
gradle build
```

The JAR will appear in `build/libs/lumenwild-1.0.0.jar`.
The `generateTextures` Gradle task creates all PNG assets automatically before resource processing.

## Source structure

- `src/main/java/dev/wiktor/lumenwild/LumenwildMod.java`: registration, entry logic, equipment.
- `src/main/resources/data/lumenwild/dimension`: dimension definition.
- `src/main/resources/data/lumenwild/worldgen`: custom biome and world-generation features.
- `src/main/resources/data/lumenwild/recipe`: survival recipes.
- `src/main/resources/assets/lumenwild`: textures generated at build time, block/item models, localizations.

No Mixins are needed for this mod.
