# Unbound Horizons 0.4.1 Experimental Extended World Height (Fabric 1.21.4)

Experimental Y=-4096 through Y=4095 (8192 blocks), **real engine
bit allocation**. Overworld natural terrain generates down to -4096.
Built-in LOD removed; use Distant Horizons independently.

## Changes over 0.4.0
0.4.0 tested 8192-block world generation and demonstrated independent
block placement at -3000 and +1096. Saving/reloading was still broken.
The mapped game bytecode revealed the second height limit: the vanilla
SerializedChunk saves the absolute section Y as an 8-bit NBT Byte. This
overflows if Y<-2048 or Y>2047.

0.4.1 adds Mixin patches to read section positions as NBT Int (including
vanilla NBT Byte numerics) and to write full integer section identifiers,
avoiding accidental section -188 / +68 collisions. The CI tests a clean
server stop, server restart, and persistence of two independent block states.

## Limits and safety
- This is NOT literally infinite height. Further engine changes needed
  beyond the 14-bit packed Y range (-8192..8191).
- Repacking Y requires reducing X/Z from 26 bits to 25 bits (safe
  +-16.7M blocks horizontally); the world border diameter becomes 30M.
- **Not safe for existing worlds.** Only test with a new backed-up world:
  packed positions and section-tag format differ from vanilla.
- Distant Horizons client interoperability **has not been verified**.
- Nether and End height span expands, but their original noise settings
  do not generate the entire expanded height.
- Rendering, saving, light, block entities and networking require long-term testing.
- Minecraft 1.21.4 + Java 21 + Fabric Loader + Fabric API.

The production JAR is published only if Gradle compiles and the full
server startup, independent position and clean save/reload test pass.
