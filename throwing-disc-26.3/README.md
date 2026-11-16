# Throwing Discs for Minecraft Java 26.3
Fabric mod with copper, iron, golden, diamond, and netherite discs. Pixel-perfect 16×16 user-provided textures. Throw with right-click (works from offhand), damage entities, pierce targets, ricochet off blocks up to 3 times, return automatically, and lose durability per flight. Values are provisional until Mojang publishes final stats.
## Build
Run `./gradlew build` with JDK 25; output in build/libs. Requires Fabric Loader and Fabric API. Recipe progression uses standard smithing and shaped crafting.

## Included refinements (v1.0.0)
- Adds a 10-tick (0.5 second) cooldown between throws with the vanilla cooldown indicator.
- Rotates discs upright in THIRD-PERSON (F5 and other players' view) in both hands using a -90-degree X-axis model transform.
- FIRST-PERSON hand display is inherited from Minecraft's regular generated item model (original unmodified look); GUI/ground appearance stays inherited too.
- Restores default third-person generated-item size (0.55 scale) instead of the enlarged previous override.
- The inventory and GUI textures remain unchanged.

The version stays at 1.0.0 for subsequent updates unless the maintainer explicitly requests a bump after publishing on Modrinth.
