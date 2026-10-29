# Unbound Horizons - Minecraft Java 1.21.4 (Fabric)

An experimental vertical-build-range extension and explored-terrain LOD cache.

## Included features

- World dimension type ranges are changed to Y=-512 through Y=1535 (2048 blocks).
- Newly generated bedrock placements are converted to deepslate (no unbreakable bedrock).
- The vanilla below-world damage handler is cancelled where applicable.
- A client LOD renderer remembers terrain columns while chunks are loaded and draws coarse 4x4 block quads after vanilla chunks unload, up to ~128 chunks away.
- The vanilla Render Distance slider is hidden when it is possible to identify the translated UI label. Detailed terrain is fixed at 8 chunks.
- Fabric API is required.

## Important limitations

**This is not infinite Y.** Minecraft's chunk/section model, height-bound validation, and block-position/network formats have hard limits. Extending Y to arbitrary infinity requires replacing the chunk storage, light engine, generation, networking and rendering paths.

**LOD is cache-based.** Distant terrain appears only after you have visited and loaded the area; this is not a replacement for Distant Horizons and does not synthesize unexplored world terrain.

**No void damage is not the same as fully generated infinite underworld.** Beyond the dimension's minimum Y, blocks cannot be placed. New terrain still follows the vanilla noise generator's generation span, with empty space above/below its normal noise range.

Use a **new backup world**, test with other mods removed, and keep Java 21, Fabric Loader and Fabric API compatible with Minecraft 1.21.4. Do not install on a valued survival world without a backup.

The Mixin annotations are written as standard @Mixin(TargetClass.class), and Gradle Loom performs remapping to Fabric's production environment.

## Build

`gradle build` (requires Gradle 8.12.x, Java 21 and internet for Minecraft/Fabric dependencies).
