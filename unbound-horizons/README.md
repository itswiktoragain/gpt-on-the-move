# Unbound Horizons 0.3.0 — Minecraft 1.21.4 / Fabric

**World-generation only. No LOD engine, LOD renderer, LOD cache, keybindings,
fog manipulation, or render-distance overrides.**
Install **Distant Horizons** separately for distant-terrain LOD.

## What changed
- Removes the entire built-in LOD client implementation and its settings hook.
- Restores Minecraft's standard Render Distance option; Distant Horizons handles its own LOD.
- Uses the deepest vanilla-supported dimension height range:
  **Y = −2032 to +2031** (4064 blocks total).
- The **Overworld noise generator** now generates rock and caves from
  **Y = −2032 through +319**; upper space remains buildable, mostly empty.
- Continues near-vanilla Overworld generation from Y ≥ −512, while deep
  Y < −512 contains deepslate/stone with additional noise-carved caverns.
- Prevents vanilla aquifers from turning natural **cave air below Y=-64 into lava**
  while leaving explicit player-placed lava and natural Nether lava alone.
- Removes the surface rule that places new unbreakable bedrock.
- Preserves void-damage override; descending past Y=-2032 is STILL not a real
  block world. There is no true infinite-Y Minecraft 1.21.4 mod in this code.

## Important limitations
**This is NOT infinite height.** Minecraft 1.21.4 and its network, chunk,
coordinate and lighting code have hard-coded vertical ranges. Seamless endless
Y would require restructuring the game far beyond dimension JSON edits.

**Save compatibility**: worlds created using older mod versions have
different vertical section layouts. Deep rock only appears in freshly generated
chunks. Make a new world, or a backup first. Do NOT run this against valued
existing worlds without a backup.

**Performance**: up to 4064 vertical blocks per chunk is several times vanilla.
Allocate enough RAM (typically 6-8 GiB to start) and lower vanilla render
distance if generation becomes slow. Distant Horizons' support for unusually
large vertical worlds is not guaranteed.

**Worldgen**: Deep custom noise caves differ from vanilla caves. The Nether
and End retain their native noise distribution; they do not receive deep-rock
Overworld generation. Ore placements may not extend to new lower depths.

Requires Minecraft 1.21.4, Fabric Loader, Fabric API, and Java 21.

## Verification
CI builds the production-remapped JAR and starts a new **normal** Overworld.
It tests for real rock at Y=-80, -256, -512, -768, -1200, -1600 and -1952,
and checks for below-Y=-64 aquifer cave-air interception. CI also requires
there to be no widespread deep lava at those sampled positions.
A passing server test does not prove Distant Horizons client compatibility.
