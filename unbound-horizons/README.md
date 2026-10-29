# Unbound Horizons 0.2.0 (Minecraft 1.21.4 / Fabric)

**Experimental** expanded-height world and cached explored-terrain LOD.

## Changes from 0.1.1
- Render distance: keep 12 real chunks for interaction/terrain, with automatic LOD beyond.
- 8x8 LOD tiles near the vanilla horizon and 16x16 tiles farther away, replacing the old uniformly coarse, high-cost 4x4 tiles.
- Limit the LOD render to 14,000 quads and skip far geometry behind the camera.
- Cache terrain at **chunk load**, instead of relying on slow periodic sampling.
- Save cache to the local world folder (or a server-specific cache folder for multiplayer) and reload on subsequent sessions.
- During LOD drawing only, extend GPU fog to avoid having vanilla fog hide distant cached tiles. The original fog is restored immediately afterward.
- Press **L** to cycle **Light (32 chunks)**, **Balanced (64 chunks)**, and **Far (96 chunks)**. Default is Balanced.

## Important limitations

- LOD **only shows previously received terrain**. A client cannot render genuinely unexplored chunks from no information. Explore the map once to populate the cache; it will then persist.
- LOD consists of a coarse top-surface approximation, not full textured distant models or caves. Features such as trees are simplified.
- The mod expands buildable dimension range to **Y=-512..1535**, not actually infinite Y. Vanilla world generation may leave air outside its original noise span.
- Newly written bedrock is replaced with deepslate. Existing bedrock may remain.
- Void-damage handlers are overridden, but no new terrain exists below the minimum height.
- Back up worlds; the mod overrides built-in dimension types. Use on a new test world first. Not compatible with vanilla-only servers.
- Requires **Java 21**, **Minecraft 1.21.4**, **Fabric Loader**, and **Fabric API**.

CI validates Gradle compilation and world registry/server start. This does not
verify all client graphics and multiplayer compatibility.
