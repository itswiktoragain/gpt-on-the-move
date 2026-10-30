# Unbound Horizons — World-Border Y Engine Prototype

**Minecraft 1.21.4 / Fabric Loader / Java 21 / Fabric API**

## What works today

- Existing Unbound Horizons v0.4.2 behavior remains: **playable Y=-4096..+4095**,
  naturally generated lower terrain, removed bedrock, deep cave-air without
  automatic lava, no built-in LOD. Distant Horizons remains separate.
- Experimental 12-byte wide coordinates can represent all three axes at
  **-30,000,000 through +30,000,000**, without collisions at the bounds.
- New independent sparse 16x16x16 virtual storage writes block identifiers
  only for nonempty sections (with gzip compression and atomic writes).
- Lazy LRU storage limits loaded virtual sections to 128, independent of total
  theoretical vertical height.
- A Java self-test verifies 150,000 random coordinate roundtrips,
  both extreme Y coordinates, collision-prone layer pairs, 220 cache
  evictions, and multiple clean storage reopens.
- Built-in experimental diagnostic commands (OP level 2):
    `/unboundheight info`
    `/unboundheight set <x> <y> <z> <block>`
    `/unboundheight get <x> <y> <z>`

## Not yet implemented — do NOT confuse with playable world height

**This JAR does NOT make the actual Minecraft world 60 million blocks tall.**
The new sparse storage is a separate experimental engine component. The
diagnostic commands store independent *virtual records*, NOT visible normal
Minecraft blocks. They cannot be mined, rendered, collided with or used by
vanilla world generation. Do not mistake a virtual-storage test pass for
a finished infinite-height mod.

Minecraft's ordinary chunk columns use a section array sized by dimension
height, which cannot be made 60 million blocks tall without replacing the
entire native chunk/storage/network/light/heightmap architecture.
Distant Horizons integration also needs engineering after native cubic
chunk support exists. See `docs/world-border-y-engine.md`.

## Data safety

- The existing v0.4.2 engine also changes packed BlockPos Y bits and the
  section Y NBT type; it is **not suitable for existing vanilla worlds**.
- Create a new throwaway Minecraft world. Existing-world corruption is
  possible if you use the underlying 0.4.2 engine on a valued save.
- Never install the 0.5.0 preview alongside a separate Unbound Horizons
  JAR; both use the same mod ID.
- Virtual test data is saved under the world folder at
  `unboundhorizons/wide_sections/<dimension>/`.
- This build is an **early backend prototype**, not a working complete
  world-border-Y gameplay release.
