# World-border Y: requirements for a real 60,000,000-block world

**Target:** Native Minecraft block positions from
Y=-30,000,000 through Y=30,000,000, while preserving normal X/Z border
coordinates and retaining separate Distant Horizons compatibility.

## Why the 0.4.2 approach cannot scale

The existing mod repacks a 64-bit block position as 25-X / 14-Y / 25-Z.
Even a 26-bit Y allocation (needed for +/-30M) would force X/Z
down to 19 bits each. That would collapse the horizontal world range to
roughly +/-262k. Keeping all three axes at +/-30M requires a minimum
of 78 bits and thus a new 96-bit/128-bit position key or three 32-bit ints.

A single 60M-height native chunk column also contains
(60,000,001 / 16) ~= **3,750,001 sections**. Preallocating millions
of slots in each loaded column is impractical, even if most are air.

## Completed in this prototype

1. `WideBlockPos` stores three 32-bit signed coordinates and round-trips
   through a 12-byte canonical representation without the 64-bit collision.
2. `SparseVerticalStore` stores just touched 16x16x16 sections, with
   GZIP persistence, an LRU cache, coordinate namespacing and atomic writes.
3. A plain-Java self-test exercises both Y borders and persistence.
4. Diagnostic commands allow checking virtual records in Minecraft without
   changing the playable world's blocks.

## Required before playable extended Y

1. Replace all native `BlockPos.asLong()` consumers that assume
   64-bit packed X/Y/Z (including maps and network codecs).
2. Rewrite `ChunkSection[]` and `WorldChunk` to use sparse
   **cubic sections**, indexed by (chunkX, sectionY, chunkZ).
3. Replace code that scans `getBottomY()..getTopY()`, allocates
   entire height arrays, or transmits all vertical sections.
4. Implement local view-window streaming and vertical chunk tickets.
5. Adapt noise population, biome sampling, caves, structures, lighting
   propagation, heightmaps and entity collision to sparse cubic sections.
6. Build a durable versioned on-disk region format keyed by 3D chunk location.
7. Define network negotiation so old clients / servers refuse incompatible
   worlds rather than silently corrupt positions.
8. Integrate cubic sections into the client renderer and Distant Horizons.
9. Add repeated save/reload tests, player falls across -4096, +4096,
   +/-30M, and memory benchmarks with mostly empty vertical space.

**No false completion claims:** passing sparse-storage tests does not
prove native gameplay works at Y=30M. The remaining work is a major
Minecraft engine replacement, not another dimension JSON setting.

## Updated border model (0.5.1-preview)

The future native world uses a **16-block Y safety buffer**:
- Native playable space will stop at Y=-29,999,984 / +29,999,984.
- Y=-30,000,000 / +30,000,000 is a nonplayable absolute hard stop.
- The backend still represents coordinates at the hard stop but refuses
  block reads/writes there. Teleport and player movement must be clamped at
  the actual border, with vertical velocity cancelled outward.
- The current playable vanilla world is still -4096..4095. Runtime mixin
  safeguards have no practical effect until sparse vertical gameplay is
  implemented.
