# Unbound Horizons 0.6.0-preview — 3D Cubic Chunk Generator

Minecraft Java **1.21.4**, **Fabric**, Fabric API and Java 21.

## What actually works in Minecraft

- Native playable dimension currently spans **Y=-4096 through +4095**.
- Its Overworld noise terrain generates down to Y=-4096 (unlike
  vanilla's -64 world floor).
- The mod now injects **real deep ores** at -4096..-96 into newly generated
  Overworld chunks: iron, copper, coal, gold, redstone, lapis,
  diamond and emerald (native configured/placed features).
- Real occasional **underground water and lava lakes** are placed at
  Y=-4096..-128. Deep aquifers no longer turn every empty cave into lava,
  while independently placed lakes CAN contain fluid.
- Generated bedrock is replaced. Built-in LOD remains removed;
  Distant Horizons is separate and compatibility is not confirmed.
- Use a **NEW test world**. Existing chunks do NOT retroactively gain
  new ores or lakes.

## New 3D cubic chunk backend

The independent cubic generator divides space into **16x16x16 cubic chunks**,
indexed by **chunkX, chunkY, chunkZ**. There is no 60M-high section array.

- Natural sections generate **independently in Y** as needed.
- Deterministic noise coordinates make caves, ore veins, and wet/dry cave
  systems continue across XYZ section boundaries.
- Vertical 3D virtual biomes: dripstone grotto, crystal caverns,
  flooded depths, magma chambers, sculk trench, lush abyss,
  basalt rifts, deep slate and sky islands.
- Ore veins repeat at deep Y, including **Y=-10,000, -1,000,000,
  and near Y=-29,999,984**.
- Natural water and lava reservoirs are biome-dependent, not a blanket
  lava fill; deep chambers still contain air.
- High positive Y can form floating rocky islands.
- Only requested sections are generated; at most 128 generated cubes
  are retained, each containing exactly **4096 byte palette IDs**.
- Only user changes are saved to separate gzip-compressed per-cube
  disk files; mined air is preserved as a proper override.
- The 16-block vertical safety margin remains: virtual playable border
  at **+/-29,999,984**, hard technical stop at +/-30,000,000.

## Critical limitation

**The new cubic section engine is still VIRTUAL.** It does NOT yet replace
Minecraft's native chunk manager, lighting, physics, block interaction,
worldgen network packets, entity movement, or client rendering.
The game is NOT actually playable at Y=-29,999,984 or +29,999,984.
The only real playable extension remains Y=-4096..+4095.
A complete replacement of those subsystems is required before the full
60-million-height cubic world can be experienced in Minecraft.

## Diagnostic commands (OP level 2)

- `/cubicworld info` — status and limitations
- `/cubicworld biome 0 -100000 0` — virtual vertical biome
- `/cubicworld chunk 0 -100000 0` — one **16x16x16** virtual section,
  biome, ore/lava/water/air counts
- `/cubicworld get 0 -100000 0` — sampled generated/edited block
- `/cubicworld mine 0 -100000 0` — store virtual mined air
- `/cubicworld set 0 -100000 0 minecraft:diamond_block` — virtual edit
- `/cubicworld restore 0 -100000 0` — remove override

These commands deliberately say **VIRTUAL**. They never place a native
Minecraft block at out-of-bounds heights.

## Safety

Never open important existing saves with this version. The underlying
0.4.2 engine changes packed BlockPos Y allocation and NBT section
serialization. Always back up or create a new test world.
Do not install alongside an earlier Unbound Horizons JAR (same mod ID).
Mod interoperability, including Distant Horizons, is experimental.

See `docs/world-border-y-engine.md` for remaining engine integration work.
