# AlphaModern 0.9.0 — entity sync, modern keepalives and block-state orientation

Experimental Java 8-compatible Java agent for Minecraft Alpha 1.0.15 to connect to Minecraft 26.2 (protocol 776) servers. Previous 0.8.2 features, Microsoft account login, world projection, font crash guard and preemptive light suppression remain.

## New in 0.9.0

- Modern player info + spawn, relative movement, rotation, teleport, head turn and despawn -> Alpha 1.0.15 packets. Players show only when a valid modern profile name is available. No modern skin, metadata, swimming, poses or entity interaction support.
- Clientbound Ping gets an immediate Pong; KeepAlive receives its long response; client tick-end is sent every 50ms while playing regardless of Alpha's paused state. Chunk batch acknowledgement remains.
- Protocol-776 block-state metadata for stairs, doors, slabs, ladders, wall torches, furnaces and chests, with simplified door/open orientation. Modern trapdoors/gates become air because their thin geometry cannot be represented by Alpha. Curved stairs are approximated by straight stairs; waterlogging has no equivalent.
- Fix **Alpha clientbound 0x0D packet field order**: stance before feet Y, the opposite of the Alpha serverbound 0x0D packet. Aligns initial spawn with server-authoritative feet Y.
- A modern teleport now triggers its accepted-teleport acknowledgement **and** an immediate absolute server-space position+rotation update, without waiting for Alpha's render loop.
- Remove experimental spawn clearance that erased actual stone and desynchronized collisions. Collision compatibility with newer blocks remains approximate; server-side collisions are authoritative.
- Debug `/amdebug` includes modern player count, client tick count and keepalive age.

## Installation

Use a single agent in Prism Launcher (remove older versions):

```
-javaagent:/absolute/path/AlphaModern-776-v0.9.0.jar
```

Your existing saved Microsoft credentials should remain untouched. Do not publish those token files. 

## Test status

Local simulated server tests cover encrypted/uncompressed packet framing, player info/spawn/movement/removal, orientation metadata, chat, chunk conversion, position packet ordering, client tick end and Ping/Pong. Tested against the supplied Alpha 1.0.15 JAR bytecode. **Live Jartex compatibility and avoiding all server timeouts are not verified.**

Block-state definitions originate from the 26.2 `minecraft-data` registry. Protocol packet IDs were extracted from the supplied 26.2 JAR. See `minecraft.wiki/w/Java_Edition_protocol` and the original McDevsWiki Alpha protocol for packet field order.