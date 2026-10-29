package dev.wiktor.unboundhorizons.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.wiktor.unboundhorizons.UnboundHorizons;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Explored-terrain LOD for Fabric 1.21.4. This does NOT fabricate
 * ungenerated/unreceived chunks, and it does not replace vanilla near chunks.
 * The cached geometry is only visible when vanilla has unloaded that chunk.
 */
public final class UnboundHorizonsClient implements ClientModInitializer {
    private static final int DETAIL_CHUNKS = 12;
    private static final int[] LOD_RANGES = {32, 64, 96};
    private static final String[] LOD_NAMES = {"Light (32 chunks)", "Balanced (64 chunks)", "Far (96 chunks)"};
    private static final int CACHE_LIMIT = 24000;
    private static final int MAX_RENDER_QUADS = 14000;
    private static final int SAMPLES_PER_TICK = 12;
    private static final int CACHE_MAGIC = 0x55484C44;
    private static final int CACHE_VERSION = 2;

    private final LinkedHashMap<Long, TileChunk> observed = new LinkedHashMap<>(4096, 0.75f, true);
    private ClientWorld trackedWorld;
    private Path cacheFile;
    private boolean dirty;
    private long scanIndex;
    private int tickCounter;
    private int lodMode = 1; // Less demanding balanced mode, not the old 128-chunk extreme mode.
    private KeyBinding cycleQuality;

    private static final class TileChunk {
        final int cx, cz;
        final int[] topY = new int[16];
        final int[] colors = new int[16];

        TileChunk(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }
    }

    @Override
    public void onInitializeClient() {
        cycleQuality = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.unboundhorizons.lod_quality", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_L, "category.unboundhorizons"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientChunkEvents.CHUNK_LOAD.register(this::onChunkLoad);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveCache());
        WorldRenderEvents.LAST.register(this::onRender);
    }

    private Path identifyCacheFile(MinecraftClient client, ClientWorld world) {
        String dimension = world.getRegistryKey().getValue().toString().replaceAll("[^A-Za-z0-9_-]", "_");
        try {
            if (client.getServer() != null) {
                return client.getServer().getSavePath(WorldSavePath.ROOT)
                    .resolve("unboundhorizons_lod")
                    .resolve(dimension + ".bin.gz");
            }
            if (client.getCurrentServerEntry() != null) {
                String address = client.getCurrentServerEntry().address;
                String serverKey = Integer.toHexString(address.hashCode());
                return client.runDirectory.toPath().resolve("config")
                    .resolve("unboundhorizons_lod")
                    .resolve(serverKey + "_" + dimension + ".bin.gz");
            }
        } catch (Exception e) {
            UnboundHorizons.LOG.warn("Could not locate LOD cache directory: {}", e.toString());
        }
        return null;
    }

    private void changeWorld(MinecraftClient client, ClientWorld world) {
        if (world == trackedWorld) return;
        saveCache();
        trackedWorld = world;
        observed.clear();
        cacheFile = world == null ? null : identifyCacheFile(client, world);
        tickCounter = 0;
        scanIndex = 0;
        dirty = false;
        loadCache();
    }

    private void onChunkLoad(ClientWorld world, WorldChunk chunk) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != world) return;
        changeWorld(client, world);
        capture(world, chunk);
    }

    private void capture(ClientWorld world, WorldChunk chunk) {
        ChunkPos cp = chunk.getPos();
        TileChunk data = new TileChunk(cp.x, cp.z);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int zz = 0; zz < 4; zz++) {
            for (int xx = 0; xx < 4; xx++) {
                int lx = xx * 4 + 2;
                int lz = zz * 4 + 2;
                int y = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, lx, lz) - 1;
                int i = zz * 4 + xx;
                data.topY[i] = y;
                if (y < world.getBottomY() || y > world.getTopYInclusive()) {
                    data.colors[i] = -1;
                    continue;
                }
                pos.set((cp.x << 4) + lx, y, (cp.z << 4) + lz);
                BlockState state = chunk.getBlockState(pos);
                data.colors[i] = state.getMapColor(world, pos).color;
            }
        }
        observed.put(ChunkPos.toLong(cp.x, cp.z), data);
        dirty = true;
        while (observed.size() > CACHE_LIMIT) {
            Iterator<Map.Entry<Long, TileChunk>> it = observed.entrySet().iterator();
            it.next();
            it.remove();
        }
    }

    private void onTick(MinecraftClient client) {
        changeWorld(client, client.world);
        if (client.world == null || client.player == null) return;
        tickCounter++;

        while (cycleQuality.wasPressed()) {
            lodMode = (lodMode + 1) % LOD_RANGES.length;
            client.player.sendMessage(Text.literal("Unbound Horizons LOD: " + LOD_NAMES[lodMode]), true);
        }

        // Vanilla high-detail chunks remain necessary for mobs, hitboxes, caves
        // and lighting. Force a modest radius; the LOD cache handles old terrain.
        if (client.options.getViewDistance().getValue() != DETAIL_CHUNKS) {
            client.options.getViewDistance().setValue(DETAIL_CHUNKS);
        }

        // Periodically refresh edited nearby blocks; freshly arrived chunks are
        // already captured immediately by CHUNK_LOAD (no more scan race).
        int cx = client.player.getBlockX() >> 4;
        int cz = client.player.getBlockZ() >> 4;
        int diameter = DETAIL_CHUNKS * 2 + 1;
        int total = diameter * diameter;
        for (int sample = 0; sample < SAMPLES_PER_TICK; sample++) {
            int n = (int) ((scanIndex++) % total);
            int x = cx + (n % diameter) - DETAIL_CHUNKS;
            int z = cz + (n / diameter) - DETAIL_CHUNKS;
            long key = ChunkPos.toLong(x, z);
            if (observed.containsKey(key) && tickCounter % 120 != 0) continue;
            WorldChunk chunk = client.world.getChunkManager().getWorldChunk(x, z);
            if (chunk != null) capture(client.world, chunk);
        }
        if (tickCounter % 600 == 0 && dirty) saveCache();
    }

    private static int averageColor(TileChunk chunk, int sx, int sz, int step) {
        long r = 0, g = 0, b = 0;
        int count = 0;
        for (int z = sz; z < sz + step; z++) {
            for (int x = sx; x < sx + step; x++) {
                int c = chunk.colors[z * 4 + x];
                if (c == -1) continue;
                r += c >> 16 & 255;
                g += c >> 8 & 255;
                b += c & 255;
                count++;
            }
        }
        if (count == 0) return -1;
        return ((int)(r / count) << 16) | ((int)(g / count) << 8) | (int)(b / count);
    }

    private static float averageHeight(TileChunk chunk, int sx, int sz, int step) {
        long total = 0;
        int count = 0;
        for (int z = sz; z < sz + step; z++) {
            for (int x = sx; x < sx + step; x++) {
                int i = z * 4 + x;
                if (chunk.colors[i] == -1) continue;
                total += chunk.topY[i];
                count++;
            }
        }
        return count == 0 ? 0 : (float)total / count + 1.015f;
    }

    private int drawChunk(TileChunk chunk, int dist, VertexConsumer vertices, Matrix4f matrix, int remaining) {
        // 8x8 tiles at short/medium range; one averaged 16x16 tile at distance.
        // The old version always drew sixteen stark 4x4 tiles per chunk.
        int step = dist <= 28 ? 2 : 4;
        int produced = 0;
        for (int z = 0; z < 4; z += step) {
            for (int x = 0; x < 4; x += step) {
                int color = averageColor(chunk, x, z, step);
                if (color == -1) continue;
                float y = averageHeight(chunk, x, z, step);
                float wx = (chunk.cx << 4) + x * 4;
                float wz = (chunk.cz << 4) + z * 4;
                float width = step * 4;
                int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
                // Opaque so the geometry does not transparently disappear.
                vertices.vertex(matrix, wx, y, wz).color(r, g, b, 255);
                vertices.vertex(matrix, wx, y, wz + width).color(r, g, b, 255);
                vertices.vertex(matrix, wx + width, y, wz + width).color(r, g, b, 255);
                vertices.vertex(matrix, wx + width, y, wz).color(r, g, b, 255);
                produced++;
                if (produced >= remaining) return produced;
            }
        }
        return produced;
    }

    private void onRender(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || observed.isEmpty()) return;
        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;
        Vec3d camera = context.camera().getPos();
        Vec3d look = client.player.getRotationVec(1.0f);
        int pcx = client.player.getBlockX() >> 4;
        int pcz = client.player.getBlockZ() >> 4;
        int range = LOD_RANGES[lodMode];
        int rangeSquared = range * range;
        List<TileChunk> near = new ArrayList<>();
        List<TileChunk> distant = new ArrayList<>();
        for (TileChunk chunk : observed.values()) {
            int dx = chunk.cx - pcx;
            int dz = chunk.cz - pcz;
            int distSquared = dx * dx + dz * dz;
            if (distSquared > rangeSquared) continue;
            int dist = Math.max(Math.abs(dx), Math.abs(dz));
            if (dist < DETAIL_CHUNKS - 2) continue;
            if (dist < DETAIL_CHUNKS + 4
                    && client.world.getChunkManager().getWorldChunk(chunk.cx, chunk.cz) != null) continue;
            double front = dx * look.x + dz * look.z;
            if (dist > 24 && front < -8.0) continue; // Don't draw terrain far behind the camera.
            if (dist <= 28) near.add(chunk);
            else distant.add(chunk);
        }
        if (near.isEmpty() && distant.isEmpty()) return;

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Fog oldFog = RenderSystem.getShaderFog();
        VertexConsumerProvider.Immediate buffers = client.getBufferBuilders().getEntityVertexConsumers();
        try {
            // Vanilla's fog end is based on the vanilla chunk radius. Without
            // this override cached far LOD fades out just like the missing chunks.
            RenderSystem.setShaderFog(new Fog(
                16000.0f, 20000.0f, oldFog.shape(),
                oldFog.red(), oldFog.green(), oldFog.blue(), oldFog.alpha()
            ));
            VertexConsumer vertices = buffers.getBuffer(RenderLayer.getDebugQuads());
            int drawn = 0;
            for (TileChunk chunk : near) {
                int dist = Math.max(Math.abs(chunk.cx - pcx), Math.abs(chunk.cz - pcz));
                drawn += drawChunk(chunk, dist, vertices, matrix, MAX_RENDER_QUADS - drawn);
                if (drawn >= MAX_RENDER_QUADS) break;
            }
            if (drawn < MAX_RENDER_QUADS) {
                for (TileChunk chunk : distant) {
                    int dist = Math.max(Math.abs(chunk.cx - pcx), Math.abs(chunk.cz - pcz));
                    drawn += drawChunk(chunk, dist, vertices, matrix, MAX_RENDER_QUADS - drawn);
                    if (drawn >= MAX_RENDER_QUADS) break;
                }
            }
            if (drawn > 0) buffers.draw(RenderLayer.getDebugQuads());
        } finally {
            RenderSystem.setShaderFog(oldFog);
            matrices.pop();
        }
    }

    private void saveCache() {
        if (!dirty || cacheFile == null) return;
        try {
            Files.createDirectories(cacheFile.getParent());
            Path tmp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(CACHE_MAGIC);
                out.writeInt(CACHE_VERSION);
                out.writeInt(observed.size());
                for (TileChunk chunk : observed.values()) {
                    out.writeInt(chunk.cx);
                    out.writeInt(chunk.cz);
                    for (int i = 0; i < 16; i++) {
                        out.writeInt(chunk.topY[i]);
                        out.writeInt(chunk.colors[i]);
                    }
                }
            }
            Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException error) {
            UnboundHorizons.LOG.warn("Could not save LOD cache at {}: {}", cacheFile, error.toString());
        }
    }

    private void loadCache() {
        if (cacheFile == null || !Files.exists(cacheFile)) return;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(cacheFile)))) {
            if (in.readInt() != CACHE_MAGIC || in.readInt() != CACHE_VERSION) return;
            int count = in.readInt();
            if (count < 0 || count > CACHE_LIMIT) return;
            for (int i = 0; i < count; i++) {
                TileChunk chunk = new TileChunk(in.readInt(), in.readInt());
                for (int j = 0; j < 16; j++) {
                    chunk.topY[j] = in.readInt();
                    chunk.colors[j] = in.readInt();
                }
                observed.put(ChunkPos.toLong(chunk.cx, chunk.cz), chunk);
            }
            UnboundHorizons.LOG.info("Restored {} cached LOD chunks", observed.size());
        } catch (IOException error) {
            observed.clear();
            UnboundHorizons.LOG.warn("Could not read LOD cache: {}", error.toString());
        }
        dirty = false;
    }
}
