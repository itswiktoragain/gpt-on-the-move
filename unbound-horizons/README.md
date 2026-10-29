# Unbound Horizons 0.2.1 (Minecraft 1.21.4 / Fabric)

**Experimental** extended-height overworld with cached previously visited terrain LOD.

## Generation fix in 0.2.1

Vanilla Minecraft 1.21.4 separates the dimension **build height** from its
noise generator's vertical range. Changing the dimension to Y=-512..1535 in
0.1/0.2 let players place blocks below -64, but DID NOT generate stone there.

This version overrides Minecraft 1.21.4's `worldgen/noise_settings/overworld.json`:
- Noise min Y: -512 (was -64).
- Noise height: 832 (was 384), generating blocks through Y=319.
- The bedrock-floor noise taper is shifted down to Y=-512..-488.
- Underground noodle-cave density sampling now reaches Y=-508.
- The dimension allows block placement up through Y=1535, **but the
  upper region is not automatically full of generated mountains**.
- Some ore generation placements follow the old vanilla distribution and
  may not be uniform at the new depths.

New generation only affects **new chunks and new worlds**, not existing empty
underground sections in already generated chunks. Back up the world first.

## LOD improvements in 0.2.0 (included here)
- 12 detailed chunks with explored terrain surface LOD up to 64 chunks by default.
- Press **L**: Light (32), Balanced (64), Far (96).
- Captures chunks as soon as they load and saves them to disk.
- Reduces tile detail as distance grows: 8x8 near, 16x16 far.
- Extends fog specifically for LOD drawing so cached tiles don't vanish due
  to the normal short draw-distance fog.
- LOD cannot render terrain that has never reached the client; unexplored
  areas stay empty until visited and cached.

## Limits

This is NOT an actually infinite-height world. The dimension builds from
-512 through +1535; the Overworld generates normal terrain from -512 through
+319. The Nether and End retain their own noise generation spans.
Already-generated bedrock is not removed; newly generated bedrock is replaced
with deepslate. The mod changes built-in dimension registries and may conflict
with other worldgen mods or unmodified servers.

Requires Minecraft 1.21.4, Java 21, Fabric Loader and Fabric API. Use a
fresh backed-up test world. CI now starts a **normal noise-generated Overworld**
and checks for real solid blocks at Y=-80, -160, -320 and -480 before publishing
the JAR. A passing CI build still does not prove complete client rendering
compatibility or long-term save stability.
