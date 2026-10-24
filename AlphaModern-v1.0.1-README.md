# AlphaModern 1.0.1 — Jartex Spawn Synchronization / Collision Recovery

**Java 8 agent for Minecraft Alpha 1.0.15 → Minecraft Java 26.2 (protocol 776).** The Minecraft Alpha jar stays unchanged on disk. This is experimental compatibility translation, not a modern Minecraft client.

## What changed in v1.0.1

- **Wait for the authoritative player chunk:** Earlier builds forwarded the first server teleport before the spawn terrain was ready, and sent `PLAYER_LOADED` after any translated chunk, even a completely different chunk. The new bridge waits for the player's own chunk, sends that terrain to Alpha first, and *then* sends Alpha's real feet/stance position and the modern `PLAYER_LOADED` response. `ACCEPT_TELEPORTATION` and the exact server-space initial position are still sent immediately to the modern server so the modern login state machine does not stall.
- **Early Alpha movement is gated:** During the join/loading window Alpha can move or fall inside its own empty placeholder world; those stale coordinates are no longer forwarded to the modern server or used as the base for relative teleports. Digging/placement is likewise paused until the spawn terrain is ready.
- **Short-lived local spawn collision exception:** `jw.class` now routes collision queries through a tiny shim that delegates to Alpha's original world physics, except for boxes that physically overlap the *server-authorized original spawn body*. It preserves floor and adjacent walls and deactivates after the player moves away or eight seconds. It never changes the server-side block state, position, local chunk cache, or any client's block packets. This is a compatibility workaround for unsupported modern partial shapes, not a bypass of server collisions.
- **Respawn/world-switch synchronization:** Modern `RESPAWN` packets are now handled. Old chunks are unloaded, stale block-state overlays and entities are cleared, and the client waits for the destination's new authoritative position/chunk before sending `PLAYER_LOADED` again. Useful on proxy lobby/world transfers.
- **Safe fallback:** If the modern server never delivers a chunk at the spawn coordinate, after eight seconds a diagnostic warning is printed and the bridge falls back to the prior positioning behavior rather than hanging indefinitely. The missing chunk still means collision may be incorrect.
- **Diagnostics:** `/amdebug` now includes `spawnReady`, `spawnFallback` and `spawnCollisionFiltered` alongside the existing world/packet counters.

## Previous v1.0.0 fixes retained

- **Alpha client-side predictions suppressed:** `gj.class` has three optimistic local block setters `a(IIII)Z`, `a(IIIII)Z`, `c(IIII)Z`. The Java agent intercepts these three methods and returns false without modifying the world. The **server update entry point** `gj.c(IIIII)Z` and all packet-driven chunk updates are unchanged. Ordinary single-player `cj.class` is untouched.
- **Server-owned chunk cache:** Clientbound protocol-776 `BLOCK_UPDATE` (8) and `SECTION_BLOCKS_UPDATE` (84) update the projected Alpha chunk cache, including the block's metadata, before reaching Alpha. Previous builds only did this for section updates. The cache is replayed correctly on modern light resends and when the 128-block vertical viewport moves.
- **Authoritative update overlays:** When Alpha's vertical viewport changes and original server chunks are reprojected, all subsequent server block updates are overlaid before a chunk is sent; old block states no longer reappear. Overlays are reset on dimension change, chunk replacement, and unload.
- **No more swallowed placements:** Alpha legacy placement packet 0x0F is translated to modern protocol-776 `USE_ITEM_ON` (66) with block position, face, centered cursor coordinates, and prediction sequence. Right-clicking empty space uses modern `USE_ITEM` (67).
- **Digging/acknowledgement:** Alpha legacy dig packet 0x0E is translated to modern `PLAYER_ACTION` (41) for start, abort, and finish; actions have monotonically increasing sequence numbers rather than the hard-coded zero in older agents. Modern clientbound `BLOCK_CHANGED_ACK` (4) is processed cumulatively; affected Alpha blocks are reconciled from the last *server-confirmed* chunk cache, not a locally predicted state. The acknowledgement does not mean the action succeeded.
- **Collision-aware 26.2 mapping:** All 5,087 states marked as `boundingBox: empty` by the 26.2 block registry are detected. When an otherwise non-colliding state would be rendered as an Alpha solid full cube, it becomes air instead of a ghost wall. Liquids, fire, torches, redstone, vines, and other non-solid approximations are retained. Real solid barrier blocks are shown as glass instead of invisible air so the player's collision agrees with the server more closely.
- Previous features including Microsoft sign-in, chat formatting, entity sync, orientation metadata, chunk rendering, modern keepalives and lighting storm prevention remain.

## Use

Use exactly ONE AlphaModern agent in Prism Launcher's JVM arguments:

```
-javaagent:/home/YOUR_USER/Downloads/AlphaModern-776-v1.0.1.jar
```

Your saved Microsoft authentication is not changed. Run `/amdebug` in-game to see pending block changes, block acknowledgements and correction counts. No need for texture packs or a replacement Minecraft jar.

## Tests / limitations

- `sh test.sh` compiles with Java 8 target bytecode and runs all protocol simulation and regression tests. New spawn tests simulate a teleport before chunks, an unrelated chunk, delayed arrival of the correct player chunk, and a modern respawn/world switch. They verify that `PLAYER_LOADED` is gated and that no old-world geometry survives respawn. The new `jw.class` bytecode transform is checked against the supplied Alpha JAR, with a separate load-time verification test. Collision-filter tests check that the floor and adjacent walls are not removed. New tests check the 3 exact Alpha class prologues while leaving the server mutation method intact, rejected placements, cumulative acknowledgements, block metadata, world projection, direct modern `BLOCK_UPDATE` and `SECTION_BLOCKS_UPDATE`, modern dig / place packet encoding, increasing sequences, modern powder snow, water and barriers.
- `gj.class` bytecode passes `java -Xverify:all` when loaded alongside the supplied Alpha 1.0.15 jar.
- The live JartexNetwork world is not available in automated tests. This is **not a confirmed end-to-end fix** for every spawn collision or physics mismatch on that server.
- Minecraft Alpha 1.0.15 lacks most modern block shapes and the agent's inventory/held-item mapping is incomplete. A placement may be rejected if the server believes a different item is held; it should no longer appear permanently only on the client. There is no guaranteed geometric match for modern fences, trapdoors, waterlogged states, barriers or unusual server-side collision rules.
- Do not share `~/.minecraft/alphamodern/credentials.properties` or token data when sending crash logs.

Public protocol references: https://minecraft.wiki/w/Minecraft_Wiki:Projects/wiki.vg_merge/Protocol and https://protocol.derklaro.dev/ (26.2 packet IDs checked against supplied client jar). Block-state registry used for collision ranges: https://github.com/Complexity-ML/minecraft-data-26.2 .