# Throwing Discs for Minecraft Java 26.3
Fabric mod with copper, iron, golden, diamond, and netherite discs. Pixel-perfect 16×16 user-provided textures. Throw with right-click (works from offhand), damage entities, pierce targets, ricochet off blocks up to 3 times, return automatically, and lose durability per flight. Values are provisional until Mojang publishes final stats.
## Build
Run `./gradlew build` with JDK 25; output in build/libs. Requires Fabric Loader and Fabric API. Recipe progression uses standard smithing and shaped crafting.

## Included refinements (v1.0.0)
- Adds a 10-tick (0.5 second) cooldown between throws with the vanilla cooldown indicator. All five materials share one `throwingdisc:all_throwing_discs` cooldown group, so swapping to a different material or hand does not bypass it.
- Third-person discs use an upright wrist-hanging grip: removed the 55-degree Z roll that previously made them look like diamonds, while preserving the face-visible Y orientation in each hand.
- FIRST-PERSON remains inherited from the standard generated model (unchanged).
- The third-person held scale is now 0.9 (was 0.55) for all five materials; hand rotation and translation remain unchanged.
- The inventory and GUI textures remain unchanged.

The version stays at 1.0.0 for subsequent updates unless the maintainer explicitly requests a bump after publishing on Modrinth.

## 3D disc flight and holding (Minecraft 26.3)
- Thrown projectiles use a dedicated third-dimensional ring model with 18 textured voxel sections and a physically open center; the ring is exactly 1 model pixel thick.
- Flying discs stay horizontal and do NOT spin. Their Y-axis orientation is captured from the player's heading at the instant of release, synced to all clients, and never updates with bounces, returning, or player turns.
- The 3D model is also used for the third-person handheld disc (both hands), preserving existing hand transforms; GUI and first-person still use the original 16x16 sprite.
- All five materials preserve the 10-tick shared cooldown and version 1.0.0.

## Flight visual refinements (still v1.0.0)
- The thrown disc now renders at 0.5 scale. Its voxel model spans 14/16 of a block, so its physical diameter in flight is 0.4375 blocks, matching its 0.5-block collision box. Previous 1.1 scale was almost a full block across because the trailer disc was shown close to the camera. Third-person held size is unchanged.
- Reduced ring thickness from 2.5 to 1.0 model pixels, keeping an open center.
- Removed animation rotation entirely so the world-aligned pixelated outline does not shimmer or rotate off-grid.

## Held pose and captured heading (still v1.0.0)
- Both third-person hands show the disc upright with zero Z roll (formerly +55/-55 degrees); no first-person model changes.
- Every thrown disc stores the throwing player's launch yaw in a synchronized float. The flight renderer applies that angle once and keeps it fixed, without spin or movement-based tracking.
- Projectile scale remains 0.5, model thickness one pixel, shared cooldown ten ticks, and mod version 1.0.0.

## Third-person held-size adjustment (still 1.0.0)
- Increased only the held model scale (right and left third-person hand) from 0.55 to 0.9, around 64% larger; this gives a model-ring diameter of about 0.79 blocks. This is independent of the flight render scale (0.5) and does not affect the first-person appearance, inventory or GUI.
