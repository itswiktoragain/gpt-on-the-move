# Unbound Horizons for Fabric Minecraft 1.21.4

0.1.1 fixes the invalid `monster_spawn_light_level` overworld dimension type
that crashes world settings on 0.1.0.

The mod uses an expanded Y range from -512 through +1535 (not infinite),
replaces generated bedrock with deepslate via the chunk section write path,
and prevents vanilla void damage. The client remembers explored terrain surface
columns and renders 4x4 distant LOD quads beyond the high-detail view. Its LOD
does not render unexplored terrain or full caves and is experimental.

**Back up worlds.** Test new worlds first. The built-in dimension override
makes this unsuitable for connecting to unmodified multiplayer servers.

The Render Distance control is hidden where recognised and vanilla full-detail
chunk radius is held at 8; LOD cannot replace the near-detail chunks required
for physics, mobs, and interaction.

Requires Java 21, Fabric Loader, Fabric API compatible with Minecraft 1.21.4.

The GitHub Actions workflow compiles the mod and starts an integrated Fabric
dedicated server with a new flat world before publishing a JAR. Server smoke
testing does not prove client rendering compatibility, so further game testing
is still advised.
