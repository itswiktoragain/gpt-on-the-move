# CursedCraft 1.2.0 — Fabric for Minecraft 1.21.4

**EXTREME WARNING: Use a disposable Minecraft instance and throwaway worlds only.** Death or fully shrinking legs starts a **five-second** actual alternate-seed terrain corruption followed by deletion of the active single-player world and a forced exit. Don't put the mod in an instance containing important saves. The title screen requires acknowledgement of this risk.

Features:
- Walking gradually shrinks the player's legs.
- Hostile mannequin entities with YouTuber Minecraft skins loaded from the Mojang profile API and the vanilla skin manager. Mannequins convulse their head, body, and arms instead of using zombie animations. Being hit by one throws a hostile clone of you.
- Death switches immediately into third-person and cancels the vanilla death screen.
- When you mine a block, it visually rises to Y 200, grows 31 times, and converts to a **31 × 31 × 31 cube** of falling blocks with the *original mined block state*. That is 29,791 physics entities per block, streamed in batches — extreme CPU/GPU cost is expected.
- Random world-border teleport glitches.
- Riding any boat sends it up to Y 200, warps it at tremendous speed to the world border, ejects the player, places a witch hut, and starts locally regenerating walls and holes to mimic legacy Far Lands within 100 blocks of the world border.
- A rare giant mannequin with your actual player skin spawns. Close proximity causes both render glitches and seeded chunk corruption; touching it forces you into a cursed boat.
- Vanilla world generation is not disabled. The apocalypse injects genuine alternate-seed base noise into nearby chunks, including previously ungenerated chunks. Far Lands-style columns are simulated as the original Far Lands overflow bug is absent in 1.21.4.

Build: `cd cursedcraft && gradle build` using Java 21 and Gradle 8.12. Install only the `cursedcraft-1.2.0.jar` (not `-sources.jar`) in a Fabric 1.21.4 instance alongside Fabric API.

Configuration: `config/cursedcraft.json`. For testing without world deletion, change `deleteCurrentSingleplayerWorld` to `false`. That still exits Minecraft at the end of the catastrophe.

**Warning:** The 31³ falling-block effect is intentionally very heavy; each burst may take several minutes to finish spawning. The full cube is capped at two queued bursts. Far Lands generation occurs only near the destination, not the entire huge world-border circumference.
