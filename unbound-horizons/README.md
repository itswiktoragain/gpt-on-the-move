# Unbound Horizons v0.4.2 (experimental)

Fabric 1.21.4, Java 21 and Fabric API.

Extends dimension build height to Y=-4096..+4095, and extends Overworld
noise generation to Y=-4096 (not actually infinite).

This experimental engine fork modifies packed positions from
26+12+26 bits (X/Y/Z) to 25+14+25 bits. X and Z therefore have a
maximum safe range of about +/-16.7 million; the world border is
reduced to a 30-million-block diameter.

This build additionally changes the **chunk save format**: vanilla
stores section Y as a signed NBT Byte; this version writes an NBT Int
for each section so sections -188 and +68 stay distinct. The NBT
reader accepts both numeric Byte and Int values.

The mod does not implement LOD. Use Distant Horizons separately,
although compatibility with extended height is not yet verified.
Generated bedrock is replaced and natural cave air below -64 is
kept free of vanilla aquifer lava.

WARNING: Test on a brand new world only. Back up everything. Worlds
from vanilla or older versions use incompatible block position
bit layouts; never open valued worlds with this prototype.

The GitHub Actions workflow validates normal world loading, rock
below the old -2032 limit, independent blocks at Y=-3000 and +1096,
and a graceful server save + reload with block entities checked.
