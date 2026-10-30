# Unbound Horizons 0.6.1-preview — native deep lake crash fix

## What was found in the 2026-10-30 Prism Launcher log
The 0.6.0-preview build ran with Distant Horizons 3.3.3, which logged
~1600 `RenderDataPointReducingList` errors: overlapping Y ranges and negative
height extents when fed data from the expanded -4096..4095 dimension.
This is a separate Distant Horizons interoperability problem.

The log then contained a REAL vanilla chunk generation failure:
`Requested chunk unavailable during world generation`, requesting a chunk
two columns away while the current chunk was in FEATURES generation.

## Native worldgen fix
Previous 0.6.0 `minecraft:lake` placed features could examine or modify
neighboring columns. 0.6.1 replaces those two deep LakeFeatures with
small **chunk-local** `unboundhorizons:deep_water_pocket` and
`unboundhorizons:deep_lava_pocket` features. Every read and write is
inside the same 16x16 X/Z chunk, with a 3-block safety margin. Existing
ore features remain registered, with native generation from -4096..-96.

The independent 16x16x16 cubic world backend and its vertical biomes,
ores and naturally occurring water/lava basins remain unchanged; these
are still **VIRTUAL** and not rendered Minecraft native chunks.

## CRITICAL: Distant Horizons
**Do not use Distant Horizons 3.3.3 with this 8,192-height mod yet.**
This version does not patch DH's internal render column representation.
Remove/disable DH in your Prism instance for initial testing. DH can
have persistent invalid LOD data under each world's `data/` folder.
Move any DH SQLite databases to a backup location only AFTER copying
your save, rather than deleting or changing other world files.

## Playable and virtual heights
- Native Minecraft dimension: Y=-4096 to +4095
- Virtual cubic backend: Y=-29,999,984 to +29,999,984
- Do not confuse the virtual backend with playable blocks at extreme Y.

## Safety
Test in a new backup world; generation is not retroactive in old chunks.
Opening vanilla worlds with the repacked BlockPos 64-bit layout risks
corruption. Fabric Loader + Fabric API and Java 21 required.


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
indexed