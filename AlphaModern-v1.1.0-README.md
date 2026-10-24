# AlphaModern v1.1.0 — server transfers, sprinting, subtitles, plugin channels

Experimental Java 8 `-javaagent` for **Minecraft Alpha a1.0.15** to connect to **Minecraft Java 26.2 (protocol 776)**. Does not replace or redistribute Minecraft's original client JAR.

## New in v1.1.0

- **Server-initiated transfers**: accepts protocol 776 transfer packets during configuration and gameplay; reconnects to the requested host/port without closing Alpha's multiplayer screen. The next handshake indicates transfer intent (`3`), reuses the authenticated profile, and reloads the target server's world/players/inventory instead of retaining old chunks. Stores and answers modern transfer cookies across server hops. Capped at eight hops per Alpha session to prevent redirect loops.
- **Optional transfer controls**: `-Dalpha.modern.allowTransfers=false` rejects server transfers; `-Dalpha.modern.transferHosts=play.example.com,lobby.example.com` limits allowed transfer destinations. By default transfers are enabled and destinations are unrestricted, as requested; a malicious server could direct the client to another host.
- **Double-tap W sprinting**: a second forward press within 350 ms starts sprinting; releasing forward stops it. Injected into Alpha's actual movement-input update, not a new GUI. Sends protocol 776 player-command start/stop sprint and movement-input state to the server. Applies an approximate 1.3x local forward speed while sprinting; server anti-cheat or terrain/collision differences may cause correction/rubberbanding.
- **HUD text and item captions**: shows modern server titles, subtitles and action-bar text, plus the selected hotbar item's name for approximately 2.6 seconds after the item changes. Common item names are included; unmapped modern IDs appear as `Item #<id>` rather than inventing names. Minecraft Alpha's existing font/GUI is reused.
- **Plugin-channel compatibility**: correctly handles bounded modern `minecraft:brand`, `minecraft:register`, `minecraft:unregister` and safely ignores unknown namespaced or legacy `BungeeCord` custom payloads without desynchronizing the protocol. Standard server-side plugin features that use supported vanilla chat, titles, transfers, entities and world updates work through those packet types. Custom plugin-specific client UIs, proprietary binary messages, server resource packs and arbitrary client-side plugins are **not** implemented. Optional `-Dalpha.modern.pluginChannels=namespace:feature,...` advertises a list of channels, but does not invent replies or handlers for those channels.
- **Fixes earlier v1.0.2 inventory startup crash**: the cross-package `InventoryMirror` class and called method are public, with a real JVM `-Xverify:all` regression test. v1.0.3 base also retains server-authoritative inventory mirroring.

## Retained fixes and features

- Microsoft/Xbox device-code authentication (with the already-saved local profile/session), Alpha email entry and automatic browser opening; no Azure CLI required for the default option. A public OAuth client ID can be restricted by the provider; successful live sign-in is not guaranteed.
- Modern player entities, movement, teleport/respawn synchronization, ping/pong, keepalive and 20-Hz client tick markers.
- Height projection, chunk and block translation, server-authoritative block updates, block-state metadata and basic inventory/hotbar/armor sync.
- Legacy Alpha font crash guard and protection against runaway Alpha relighting. Unmapped solid modern blocks fall back to visible full-cube collisions; exact partial-block shapes and interactive modern item components remain incomplete.

## Install in Prism Launcher (Linux)

Download **`AlphaModern-776-v1.1.0.jar`** from the provided direct GitHub link. In the Alpha a1.0.15 instance, remove all earlier AlphaModern agents and add **one** JVM argument:

```
-javaagent:/home/YOUR_USER/Downloads/AlphaModern-776-v1.1.0.jar
```

Use Java 8, as for Alpha 1.0.15. Your previous saved Microsoft login credentials stay in `~/.minecraft/alphamodern/` unless explicitly logged out. Do not share token/credential files.

Optional settings examples (also JVM arguments):

```
-Dalpha.modern.transferHosts=play.jartexnetwork.com,lobby.jartexnetwork.com
-Dalpha.modern.allowTransfers=false
-Dalpha.modern.pluginChannels=example:feature
```

Only set a transfer allowlist when you know the actual destinations; server networks may use additional internal proxy hostnames.

## Build and regression tests

With a JDK installed, run `sh build.sh` from the source directory to produce `AlphaModern-776-agent.jar`. Run `ALPHA_JAR=/path/to/minecraft-a1.0.15-client.jar sh test.sh` with your own original Alpha client JAR (not included). The tests cover both auth and protocol mocks, modern server transfer across two local mock endpoints including cookie/handshake intent, modern titles and plugin messages, and JVM-verified UI and sprint hooks (`-Xverify:all`). Network and compatibility tests are **local simulated tests**, not a live JartexNetwork validation.

## Important limits

This is a reverse-engineered, **experimental** protocol bridge, not a complete 26.2 client. Proxy-specific authentication/configuration, arbitrary Bukkit/Velocity/Spigot plugin payloads, item components/custom menus, inventory edge cases, newer entity mechanics, collision shapes, and 3D rendering can behave differently from a modern client. Keep your previous agent copy if you want to compare behavior, but only enable one at a time. This release does not promise live Jartex gameplay or unrestricted compatibility with every plugin or redirect.