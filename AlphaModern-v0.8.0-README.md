# AlphaModern v0.8.0 — crash protection and terrain diagnostics

**Target:** Minecraft Alpha 1.0.15 connected to Minecraft 26.2 (protocol 776) through a Java 8-compatible `-javaagent`.

## Why v0.7.0 failed in the user's live Prism Launcher test

The supplied launcher log contains more than a thousand `Skipped incompatible chunk: null` messages, and later `StringIndexOutOfBoundsException: String index out of range: 2` in obfuscated Alpha font-renderer `js.a` (original source line 152). The later LWJGL X11 `BadWindow` occurs during window/keyboard shutdown, possibly as a consequence of the earlier crash.

These are two different bugs. A valid authenticated login does not imply that a modern server's chunk packets are compatible with our experimental translator.

## Fixes in v0.8.0

- **Verified crash fix to the Alpha font renderer:** A bytecode transformer patches `js.a(String,int,int,int,boolean)` to jump to the loop's bounds check (BCI 143) instead of straight into `String.charAt` (BCI 152) after consuming a `§` color sequence. We checked the exact original class signature and successfully disassembled the resulting patched class from the provided Alpha JAR. The game JAR on disk is not modified.
- **Chat sanitization:** Incoming modern chat is stripped of trailing incomplete color sequences, split only on safe boundaries, and rendered without the `[26.2]` tag. Rich text structures remain flattened to Alpha's supported color codes.
- **Chunk decode diagnostics:** On the first failed chunk, the agent now prints the exception class, complete stack trace, packet length, and section/stage offset when known. Later identical failures are rate-limited instead of filling the launcher log with `null` messages. Failed chunks are never sent to Alpha.
- **Defensive compatibility decoding:** First tries the exact 26.2 section structure (non-empty and fluid counts, no size-prefixed palette longs). If that fails, tries carefully validated older/proxy section representations, including sections with one count, prefixed long arrays, and 256-block-tall chunk data. Every strategy must consume *exactly* the expected number of bytes; homogeneous block counts are cross-checked to avoid accidental acceptance of misaligned data. These paths are fallbacks, not a guarantee of compatibility with every proxy.
- **Player Loaded:** Sends protocol-776 `SERVERBOUND_PLAYER_LOADED` only after the first successfully translated chunk to let the server start simulating the player.
- **In-game `/amdebug`:** Reports viewport bounds and counts of translated/rejected chunks.

**Important:** Although these changes passed local simulated tests, the live Jartex server packet bytes were not provided, so I cannot confirm that all chunks will decode. If terrain still fails, the new diagnostics will name the stage and can write one raw rejected chunk packet for the next fix.

## Installation (Prism Launcher / Linux)

In your Alpha 1.0.15 Prism Launcher instance, remove the old AlphaModern agent from the agent/mod entry list or replace its JAR. Ensure only **one** `-javaagent` argument for AlphaModern is present, pointing to `AlphaModern-776-v0.8.0.jar`. Example:

```
-javaagent:/home/YOUR_USER/Downloads/AlphaModern-776-v0.8.0.jar
```

Cached Microsoft sign-in remains under `~/.minecraft/alphamodern/` and does not need to be deleted.

### If you still see rejected chunks

Add this JVM option *before* `-javaagent`:

```
-Dalpha.modern.captureBadChunks=true
```

The first rejected packet is written into `~/.minecraft/alphamodern/debug/bad-chunk-X_Z.bin`. **This capture contains raw data from the server/world.** It does not intentionally include account tokens, but review it before sharing and do not publish your `credentials.properties`. This diagnostic capture is off by default and limited to **one** failed packet per connection.

Console output will now say, for example, `section=... offset=... stage=block palette ... EOFException`. This is much more actionable than `null`.

## Building and verification

Requires JDK 8 or later:

```
sh build.sh
sh test.sh
```

To verify the actual font class from your own Alpha JAR, set `ALPHA_JAR=/path/to/minecraft-a1.0.15-client.jar sh test.sh`, or run:

```
java -jar AlphaModern-776-agent.jar patch-font-dump path/to/minecraft-a1.0.15-client.jar js-fixed.class
javap -c -p js-fixed.class
```

The font renderer's bytecode should show `276: goto 143`, not `goto 152`.

**Known limitations:** Complete modern entities, blocks, collision rules, inventories, event packets and full-height worlds cannot be perfectly reproduced inside Alpha. This is an experimental proxy, not a mature modern Minecraft client. Do not treat mock tests as live-server verification.

Sources checked: https://minecraft.wiki/w/Chunk_format , https://minecraft.wiki/w/Formatting_codes , https://minecraftdocs.dev/systems/server/players-and-sessions , https://github.com/PrismarineJS/mineflayer/issues/3952 .

---

# AlphaModern v0.7.0 — World and chat fix for Minecraft Alpha 1.0.15

**New in v0.7.0** (protocol 776, Minecraft 26.2):

- **Player-centered vertical world window:** The modern overworld is 384 blocks tall; Alpha can draw only 128. The agent projects the 128-block slice around the player's modern Y coordinate. For example, modern Y=250 becomes Alpha Y=74; movement and digging are translated back to the true server Y. Incoming chunks are buffered until the first server teleport; cached chunks are reprojected after a large vertical teleport.
- **Correct modern chunk unloading:** Modern ChunkPos encodes X in the low 32 bits and Z in the high 32 bits; the old converter had them reversed. This caused ghost/stale chunks during movement and changes of server lobby.
- **Modern block and lighting updates:** Single- and multi-block change packets, plus standalone light updates, are translated into Alpha's block packets and 128-high chunks. A configurable ambient lighting floor defaults to 8 to prevent pitch-black terrain in unsupported modern lighting setups; use `-Dalpha.modern.ambientLight=0` for server-accurate values.
- **Real NBT text chat:** Properly decodes 26.2 binary network-NBT chat components recursively, strips `click_event` and other metadata, resolves common translation keys, converts named and RGB colors to Alpha's `§` colors, and removes the `[26.2]` prefix from normal server chat. Alpha cannot implement interactive URL clicks or modern fonts; links shown in chat may require opening manually.
- **Diagnostic command:** Type `/amdebug` in Alpha chat to show the server Y, projected Alpha Y, active vertical window, and number of cached chunks without sending the command to the server.

**Testing:** New end-to-end offline mock-server tests cover a modern Y=250 lobby, early chunk buffering, the real Alpha y-offset, NBT chat colors, modern block/light updates and correct packed chunk unloading. Pre-existing authentication, encryption, compression, chunk and runtime interception tests pass. Live public server gameplay has **not** been verified and complete modern multiplayer remains **experimental**.

**Install:** Add `-javaagent:/absolute/path/AlphaModern-776-v0.7.0.jar` to your Alpha 1.0.15 launcher JVM arguments. Replace the older JAR; you don't need to delete saved Microsoft account credentials. For console troubleshooting, start the launcher from a terminal and use `/amdebug` in game.

Technical references:
- https://minecraft.wiki/w/Java_Edition_protocol/Chunk_format
- https://github.com/ccc007ccc/minecraft-rs/blob/main/docs/protocol.md (26.2 packet position, block updates and packed ChunkPos)
- https://minecraft.wiki/w/Text_component_format (network NBT text formatting)
- https://minecraft.wiki/w/Formatting_codes (Alpha § formatting)

---

# AlphaModern 776 v0.7.0 — fixed Minecraft 26.2 terrain decoding

A Java 8-compatible `-javaagent` for **Minecraft Alpha 1.0.15** providing an experimental translation bridge to **Minecraft 26.2 (protocol 776)**. It patches Alpha's multiplayer socket and connecting GUI at runtime; no modification or redistribution of the Minecraft JAR is required.

## What's fixed in v0.7.0 (terrain)

The prior v0.5.0 screenshots showed corrupt 26.2 chunks: a ceiling of floating rock, missing ground, and huge pitch-black faces. This was a **wire protocol bug**, not a shader or texture problem.

- **Fixed protocol-776 chunk section headers.** Minecraft 26.x sends a 16-bit `nonEmptyBlockCount` **followed by another 16-bit `fluidCount`** before the paletted block states. Earlier AlphaModern consumed only the first field, putting the parser two bytes out of alignment in every section.
- **Strict packet boundary validation.** All `worldHeight/16` sections must consume exactly the given chunk-data byte array, or the chunk is rejected instead of rendering garbage. Indirect and global state palette decoding are covered by regression tests.
- **Better 26.2 block-state conversion.** A compressed lookup table maps the exact 26.2 block state IDs to closest Alpha blocks, with air, grass, dirt, cobblestone, water, lava, logs, leaves, glass, snow, wood, ores, plants, and wool metadata handled explicitly. Newer unsupported blocks are approximated or dropped rather than interpreting every non-air state as stone.
- **Real chunk lighting.** The decoder reads packet light masks and 2048-byte section light arrays, converts modern X-fastest section coordinates to Alpha Y-fastest nibble arrays, and uses full skylight as a fallback if the optional lighting payload cannot be interpreted.
- **Live block updates** now use the same state-to-block mapping and carry legacy metadata when applicable.

The table `src/alphamodern/blockmap.rle` is generated from the `data/pc/26.2/blocks.json` state ranges in https://github.com/Complexity-ML/minecraft-data-26.2 (derived from PrismarineJS minecraft-data, listed as MIT). Values are approximations for Alpha's much smaller block set. The **Minecraft 26.2 and Alpha JARs are not bundled**.

Known limitations: Alpha supports only Y=0..127, while modern Overworld terrain is Y=-64..319; modern blocks beyond Alpha's IDs and models cannot be represented faithfully. Some events, inventories, entities, dimension types, and interactions remain incomplete. Alpha can display the initial translated world, but modern multiplayer gameplay is *not* guaranteed to be fully compatible. Chunk conversion is tested against mock protocol packets, **not a live Minecraft 26.2 server**.

## Existing authentication fix from v0.5.0

The previous 0.4.0 build failed with "Azure CLI (az) is not available" before it could issue a device-link code. This release **does not require Azure CLI, an Entra tenant, or an app registration for its default sign-in attempt**.

By default, it uses the **previously published Nintendo Switch/Xbox-style first-party public client ID** `00000000441cc96b` (also used as a default by the independently developed `mcauth-go` library). It is a real pre-existing application identifier, **not a new client ID generated by AlphaModern or registered in your name**. It uses the matching legacy Microsoft consumer OAuth endpoints:

* Device code: `POST https://login.live.com/oauth20_connect.srf`
* Token and refresh: `POST https://login.live.com/oauth20_token.srf`
* Scope: `service::user.auth.xboxlive.com::MBI_SSL`
* Includes `response_type=device_code` in the initial request
* Xbox Live uses the consumer token as a raw `RpsTicket` (not the `d=` form used by custom Entra client apps)

This replicates the familiar "open Microsoft link and enter code" UX. The following Xbox Live -> XSTS -> Minecraft Services chain authenticates an **owned Minecraft Java Edition profile** and submits a real Mojang session join request for online-mode Java servers.

**IMPORTANT: This is an interoperability technique using an existing Microsoft first-party app ID. It is not endorsed by Microsoft or Nintendo, and their services may restrict or reject the ID. We could not perform a live Microsoft sign-in test in this environment, so a successfully displayed device code or completed online-mode login is NOT guaranteed.** If it is rejected, use your own properly registered/approved OAuth application ID (option below). AlphaModern does not bypass ownership, Microsoft security, or multiplayer permissions.

## Install on Ubuntu

Put `AlphaModern-776-TerrainFix-0.6.0.jar` somewhere on your computer, then add this **JVM argument** to the launcher's Minecraft **Alpha 1.0.15** instance (change the path):

```
-javaagent:/home/YOUR_USER/Downloads/AlphaModern-776-TerrainFix-0.6.0.jar
```

On the **first connection to an online-mode 26.2 server**:

1. Enter your Microsoft account email into the Alpha connecting screen and press Enter. The email is just an on-screen hint; it is **not submitted as an OAuth credential**, nor does it generate the code itself.
2. The agent requests an Xbox-style device code and displays it in Alpha. The verification URL (normally `https://www.microsoft.com/link`) opens in your browser automatically. You can use the displayed URL manually if the browser doesn't launch.
3. On the real Microsoft website, enter the code and sign in with the account **that owns Java Edition**. Never type your Microsoft password into the game.
4. If Microsoft accepts the flow, the agent retrieves your Java profile and reconnects to the server with your real username and UUID.
5. The public client ID is saved to `~/.minecraft/alphamodern/config.properties`. Your authenticated Minecraft bearer token and rotating Microsoft refresh token are cached in `credentials.properties` for reuse and refresh without re-entering a code.

The agent **does not ask for Azure CLI** during this process. Email entry (including Backspace and Ctrl+V), browser opening, device-code display, and token caching remain supported.

### Commands

```
java -jar AlphaModern-776-TerrainFix-0.6.0.jar use-switch
java -jar AlphaModern-776-TerrainFix-0.6.0.jar auth
java -jar AlphaModern-776-TerrainFix-0.6.0.jar whoami
java -jar AlphaModern-776-TerrainFix-0.6.0.jar logout
java -jar AlphaModern-776-TerrainFix-0.6.0.jar configure YOUR_APPROVED_ENTRA_CLIENT_ID
java -jar AlphaModern-776-TerrainFix-0.6.0.jar register
```

* `use-switch` explicitly chooses the public Xbox/Switch-style client ID if you previously configured an incompatible ID. It doesn't access your Microsoft credentials.
* `auth` performs the device-code sign-in from a terminal instead of the game.
* `whoami` shows public cached profile information, never secret tokens.
* `logout` deletes AlphaModern's saved login; the next online connection will ask to link again.
* `configure` sets your own registered, appropriately authorized Entra public-client GUID. The separate Microsoft Entra v2 OAuth endpoints, scopes and `d=` Xbox ticket form are used for that path.
* `register` is still included as an **optional** feature for people who explicitly want a new Entra app registration and have the Azure CLI and Entra rights. It is **not** called automatically.

If you changed your client ID since the previous version, execute `use-switch` once. A previously cached OAuth refresh token associated with a different client ID cannot be used with the Xbox-style public client and AlphaModern will request a fresh login.

## Privacy and limitations

Secret tokens are written to local `~/.minecraft/alphamodern/credentials.properties`, with owner-only permissions where the filesystem supports it. **They are not encrypted at rest.** Never send or publish that file. An access or refresh token is an authentication credential.

This remains an **experimental** Alpha-to-26.2 bridge. Initial 128-block chunk conversion, approximate modern block state mapping, basic position, keepalive, and chat are implemented; inventories, entity synchronization, signed chat, and numerous world packets are incomplete. A valid Microsoft login does **not** imply that joining a real 26.2 server will succeed or gameplay will work.

The protocol and authentication components were compiled and validated with local mock protocol-776 servers, a mock Microsoft device-code service, a fake Xbox Live/XSTS/Minecraft Services flow, a fake online session join, a fake Azure CLI for the optional registration command, and direct bytecode patch testing with the provided Alpha 1.0.15 JAR. **In addition, terrain regression tests cover realistic 26.2 section headers, palette variations, block mappings, metadata, light arrays, NBT block entity skipping and strict length validation. No real Microsoft account, public 26.2 server, or production device-code endpoint was contacted.**

## Build and test

Requirements: JDK 8 or later. From this source directory:

```
sh build.sh
sh test.sh
```

The build produces `AlphaModern-776-agent.jar`, with `Premain-Class: alphamodern.AlphaModernAgent`. No network dependencies are required to compile.

Technical references:

- https://github.com/ItsGlucose/mcauth-go — public Xbox/Switch device ID and `login.live.com` device-code protocol examples
- https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code — Microsoft general device code behavior and requirements
- https://minecraft.wiki/w/Microsoft_authentication — Minecraft Microsoft -> Xbox -> Java authentication steps