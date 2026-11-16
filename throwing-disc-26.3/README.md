# Throwing Discs for Minecraft Java 26.3
Fabric mod with copper, iron, golden, diamond, and netherite discs. Pixel-perfect 16×16 user-provided textures. Throw with right-click (works from offhand), damage entities, pierce targets, ricochet off blocks up to 3 times, return automatically, and lose durability per flight. Values are provisional until Mojang publishes final stats.
## Build
Run `./gradlew build` with JDK 25; output in build/libs. Requires Fabric Loader and Fabric API. Recipe progression uses standard smithing and shaped crafting.

## Included refinements (v1.0.0)
- Adds a 10-tick (0.5 second) cooldown between throws with the vanilla cooldown indicator.
- Holds discs upright instead of sideways/horizontally in the right or left hand, in first and third person.
- The inventory and GUI textures remain unchanged.

The version stays at 1.0.0 for subsequent updates unless the maintainer explicitly requests a bump after publishing on Modrinth.
