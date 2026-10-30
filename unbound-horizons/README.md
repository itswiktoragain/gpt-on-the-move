# Unbound Horizons 0.5.1-preview — 16-block Y border margin

Minecraft 1.21.4, Fabric, Fabric API, Java 21.

## Boundary specification

- **Actual virtual Y world border:** -29,999,984 to +29,999,984
- **Absolute coordinate hard stops:** -30,000,000 and +30,000,000
- **Safety buffer:** 16 blocks at each end
- Player-foot positions are clamped to the actual border and outgoing
  vertical velocity is zeroed at the border, as a safeguard for future
  native vertical-range integration.
- Sparse-virtual-section **writes and reads are rejected** in the 16-block
  safety buffer; records there cannot be placed.
- The 12-byte coordinate type still represents the technical +/-30M
  endpoints, but they are deliberately not playable or writable blocks.

## What is actually playable

**Current actual Minecraft gameplay remains Y=-4096..+4095.** This preview
DOES NOT make the native world 60 million blocks tall. The new border and
sparse storage form an engine prototype; vanilla chunk storage,
rendering, physics, packets and generation are not connected to the
60-million-height virtual space yet.

A server-side player tick guard and general Entity.setPosition guard are
implemented, but are not evidence that every movement path at Y=-30M is
covered: the full native cubic height engine remains unimplemented.

## Developer diagnostics (operator permissions)

- `/unboundheight info` — display limits
- `/unboundheight check -30000000` — hard stop
- `/unboundheight check -29999985` — 16-block safety buffer
- `/unboundheight check -29999984` — actual world border
- `/unboundheight set 0 -29999984 0 minecraft:stone` — write a VIRTUAL record
- `/unboundheight get 0 -29999984 0` — read a VIRTUAL record

**Virtual records are NOT actual Minecraft blocks.**

## Safety and compatibility

Do not open valued saves with this experimental mod. It still uses the
previous engine's 14-bit Y packed BlockPos and int-section-NBT changes
for the playable -4096..4095 world. Make a fresh test world.
Distant Horizons integration for extended-height worlds is still
unverified and the built-in LOD remains removed.

Tests include full-range coordinate serialization, both actual border
positions, hard-stop write rejections, forced clamp behavior, 220 LRU
evictions, persistence across restarts, and a Minecraft server startup.
