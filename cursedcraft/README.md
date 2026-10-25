# CursedCraft (Fabric 1.21.4)

**WARNING: This mod intentionally destroys the active single-player Minecraft save and force-exits Minecraft after a five-second catastrophe. BACK UP your world before testing.**

Features:
- Walk 320 horizontal blocks (configurable): legs visually shrink until gone.
- Random hostile mannequins with Minecraft YouTuber usernames, zombie AI, and player skin lookup.
- A successful YouTuber mannequin strike throws out a hostile copy of your player.
- **Dying from ANY cause OR reaching zero legs** triggers five seconds of terrain corruption: real alternate-seed terrain columns injected into full 16x16 chunk footprints (using the vanilla NoiseChunkGenerator), random blocks/mobs, and a glitching field of view.
- The regular death screen is dismissed and the camera goes into third-person.
- After five real-time seconds, the integrated server stops; the active save folder is removed and the process exits.
- Dedicated / multiplayer servers do not trigger the apocalypse.

## Compile
Install Java 21 and Gradle 8.12, then run:
```sh
cd cursedcraft
gradle build
```
The JAR is in `build/libs/`. Put it and the Fabric API for 1.21.4 into your instance's `mods` folder.

Configuration is written to `config/cursedcraft.json`. For a **non-destructive test**, set `deleteCurrentSingleplayerWorld` to `false`. This still shuts down Minecraft after five seconds.

The corruption generates previously unloaded chunks using normal world generation, then replaces chunk columns with terrain sampled from the vanilla generator configured with random alternate seeds. This includes natural terrain and cave noise, but does not import structures from the alternate seeds. On startup a large, unskippable warning screen requires explicit acknowledgement. The camera FOV glitches during the five-second sequence.
