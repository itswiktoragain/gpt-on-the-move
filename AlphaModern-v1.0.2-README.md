# AlphaModern v1.0.2 — full-cube unknown blocks + player inventory

A **Java 8 `-javaagent`** for Minecraft **Alpha 1.0.15** connected to modern Java **26.2 / protocol 776**. This is experimental protocol adaptation, not a modern client. The original Alpha game JAR is not modified on disk.

## Changes in v1.0.2

### Collision mapping / spawn

- **Unregistered or unknown block states use real Alpha stone (block ID 1)**, which is a visible full, solid cube rather than client air or an invalid ID. This also covers previously non-solid approximations of unsupported fence gates and trapdoors.
- Removed v1.0.1's **eight-second spawn collision bypass**; that bypass deliberately omitted collision shapes overlapping the player's spawn, even for solid stone/emerald blocks. The bridge no longer activates it.
- Existing known non-colliding block states (air, fluids, vegetation, fire, etc.) remain non-solid. Explicitly known collision states such as emerald blocks and beacons remain solid, generally represented with Alpha stone for blocks without exact equivalents. Invisible barriers are deliberately rendered as glass so their collision is visible.
- Existing server-controlled block updates, wait-for-spawn-chunk, vertical projection, lighting optimizations, and modern player entity support are preserved.

**Limitation:** full cubes are deliberately conservative. Server fences, slabs, trapdoors, ladders and other *partial* collision shapes may still differ from Alpha's approximations. A solid 1×1×1 collision box does not fix every possible server/client physics disagreement or a genuinely bad server spawn location.

### Experimental networked player inventory

- Mirrors protocol-776 **Set Container Content / Slot / Set Player Inventory / Cursor Item / Held Slot** into Alpha's original 36-slot survival inventory and four armor slots, including the nine-slot hotbar.
- Forwards hotbar selection (`Set Carried Item`) and **ordinary left/right clicks in the personal inventory GUI** (`Container Click`) and re-applies the server snapshot to prevent client-only phantom items.
- Translates **142 common modern item names** into existing, registered Alpha item IDs. Unavailable modern item IDs are displayed as a stone placeholder instead of crashing Alpha's renderer. The server always retains the real item ID.
- Decodes common 26.2 item payloads (such as custom names, lore, custom model data, enchantments, simple stats, and component removals) safely. These unsupported *visual properties* are discarded, while the real item ID/count still reach Alpha. Unknown complex component codecs are rejected without pretending to decode the rest of a packet.

**Limitations:** no arbitrary modern container/merchant/creative GUI translation; complex modern items, some item component types, offhand/crafting and cursor interactions remain partial. Plain player inventory is the supported first milestone. The Alpha UI cannot display modern item lore or textures faithfully. Server-side rejections may require resynchronization; this release is not guaranteed to work on all servers.

## Install on Prism Launcher / Ubuntu

Download the binary `AlphaModern-776-v1.0.2.jar` from GitHub, remove older AlphaModern agents, and set JVM arguments to:

```bash
-javaagent:/home/YOUR_USER/Downloads/AlphaModern-776-v1.0.2.jar
```

Use **one** Java agent only. Login credentials continue to be saved by the previous AlphaModern authentication code; do not publish `~/.minecraft/alphamodern/credentials.properties`.

## Source and tests

`sh build.sh` rebuilds the Java 8 agent. `sh test.sh` performs the local simulated protocol, terrain, login, chunk, player, collision, spawn, and inventory tests. The tests require the original Alpha JAR at `/mnt/data/minecraft-a1.0.15-client.jar` or an `ALPHA_JAR` environment variable pointing to your owned copy.

Not tested live on JartexNetwork. In-game `/amdebug` reports translated inventory activity and block state. Report crash logs without secret tokens.

Protocol and registry references: https://minecraft.wiki/w/Java_Edition_protocol/Slot_data and https://github.com/Complexity-ML/minecraft-data-26.2 (26.2 protocol.json / blocks.json / items.json). Minecraft Alpha itself does not provide this newer shared-inventory feature.
