# Unbound Horizons 0.4.0 Engine Height Experiment

Minecraft 1.21.4, Fabric Loader, Fabric API and Java 21.

## Actual engine modification (not just datapack height)
The mod rewrites BlockPos's packed-position bit allocation early during static
initialization, before DimensionType computes its vertical limits.
- Vanilla: 26 bits X + 12 bits Y + 26 bits Z
- Experiment: 25 bits X + 14 bits Y + 25 bits Z
- The dimension can now legally represent up to ~16,352 vertical blocks.
- This **version uses Y -4096..+4095**, 8,192 blocks total.
- Overworld stone/caves generate down to -4096. The upper region above Y=319
  remains primarily open sky. Nether/End keep their original noise bounds.
- No built-in LOD or render distance modification. Use Distant Horizons separately.
- The mod removes bedrock from new terrain and avoids deep lava-filled cave air.

## Mandatory tradeoff
This reduces **valid horizontal packed coordinates to about +/-16.7 million**
blocks on both X and Z. The mod reduces huge world borders to a safe 30-million
diameter (roughly +/-15 million X/Z), protecting from block-position collisions.
It is not mathematically possible to store wider X, Z and Y coordinates in the
same 64-bit packed structure without changing networking/storage protocols.

## Test safety
This is a work-in-progress full-engine compatibility experiment, not a
guarantee of compatibility with other mods (including Distant Horizons).
Backup your worlds or create a new world. Never open an important existing save
with the experimental binary: it changes existing BlockPos bit layout and may
alter on-disk block entity positions, packed light keys and world borders.

The GitHub build CI will release the JAR only if:
1. Production Fabric JAR compiles.
2. A new Minecraft server generates real stone below the previous -2032 limit.
3. Block positions at Y=-3000 and Y=1096 survive an independent same-XZ write
   and read (these heights collide under the old 12-bit packed Y format).
4. The same two blocks **survive a save and server restart**.
5. The below-Y=-64 aquifer conversion prevents pervasive natural lava.

Even a passing server test does not guarantee client rendering (including
with Distant Horizons), lighting, mob AI, or huge-coordinate compatibility.

## Build
`gradle build` from the unbound-horizons project folder.
