package dev.wiktor.unboundhorizons.client;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.client.util.math.MatrixStack;

/**
 * World-space LOD: draws coarse cached 4x4 horizontal tiles past the fixed,
 * high-detail view radius. It renders REAL observed top-block color/height,
 * not a fake 2D overlay. Unvisited distant chunks cannot be represented.
 */
public final class UnboundHorizonsClient implements ClientModInitializer {
    private static final int DETAIL_CHUNKS = 8;
    private static final int LOD_CHUNKS = 128;
    private static final int CACHE_LIMIT = 10000;
    private static final int MAX_RENDER_TILES = 32000;
    private static final int SAMPLES_PER_TICK = 24;
    private final LinkedHashMap<Long, TileChunk> observed = new LinkedHashMap<>(4096, 0.75f, true);
    private ClientWorld trackedWorld;
    private long scanIndex;
    private int tickCounter;

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
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        WorldRenderEvents.LAST.register(ctx -> this.onRender(ctx));
    }

    private void onTick(MinecraftClient client) {
        if (trackedWorld != client.world) {
            trackedWorld = client.world;
            observed.clear();
            scanIndex = 0;
        }
        if (client.world == null || client.player == null) return;
        tickCounter++;

        // LOD still needs nearby full-detail chunks for interaction, entities,
        // caves and real lighting. Lock that range instead of exposing a slider.
        if (client.options.getViewDistance().getValue() != DETAIL_CHUNKS) {
            client.options.getViewDistance().setValue(DETAIL_CHUNKS);
        }

        int centerX = client.player.getBlockX() >> 4;
        int centerZ = client.player.getBlockZ() >> 4;
        int diameter = DETAIL_CHUNKS * 2 + 1;
        int total = diameter * diameter;
        for (int sample = 0; sample < SAMPLES_PER_TICK; sample++) {
            int index = (int) ((scanIndex++) % total);
            int cx = centerX + (index % diameter) - DETAIL_CHUNKS;
            int cz = centerZ + (index / diameter) - DETAIL_CHUNKS;
            long key = ChunkPos.toLong(cx, cz);
            if (observed.containsKey(key) && tickCounter % 120 != 0) continue;
            WorldChunk chunk = client.world.getChunkManager().getWorldChunk(cx, cz);
            if (chunk == null) continue;
            TileChunk data = new TileChunk(cx, cz);
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int zz = 0; zz < 4; zz++) {
                for (int xx = 0; xx < 4; xx++) {
                    int lx = xx * 4 + 2;
                    int lz = zz * 4 + 2;
                    int y = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, lx, lz) - 1;
                    int cell = zz * 4 + xx;
                    data.topY[cell] = y;
                    if (y < client.world.getBottomY() || y > client.world.getTopYInclusive()) {
                        data.colors[cell] = -1;
                        continue;
                    }
                    pos.set((cx << 4) + lx, y, (cz << 4) + lz);
                    BlockState state = chunk.getBlockState(pos);
                    data.colors[cell] = state.getMapColor(client.world, pos).color;
                }
            }
            observed.put(key, data);
            if (observed.size() > CACHE_LIMIT) {
                Iterator<Map.Entry<Long, TileChunk>> iterator = observed.entrySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }
    }

    private void onRender(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || observed.isEmpty()) return;
        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;
        Vec3d camera = context.camera().getPos();
        int pcx = client.player.getBlockX() >> 4;
        int pcz = client.player.getBlockZ() >> 4;
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        var matrix = matrices.peek().getPositionMatrix();

        VertexConsumerProvider.Immediate buffers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vertices = buffers.getBuffer(RenderLayer.getDebugQuads());
        int rendered = 0;
        for (TileChunk chunk : observed.values()) {
            int dx = Math.abs(chunk.cx - pcx);
            int dz = Math.abs(chunk.cz - pcz);
            if (Math.max(dx, dz) <= DETAIL_CHUNKS
                || (long) dx * dx + (long) dz * dz > (long) LOD_CHUNKS * LOD_CHUNKS) continue;
            for (int zz = 0; zz < 4; zz++) {
                for (int xx = 0; xx < 4; xx++) {
                    int cell = zz * 4 + xx;
                    int color = chunk.colors[cell];
                    if (color == -1) continue;
                    int r = (color >> 16) & 255;
                    int g = (color >> 8) & 255;
                    int b = color & 255;
                    float x = (chunk.cx << 4) + xx * 4;
                    float z = (chunk.cz << 4) + zz * 4;
                    float y = chunk.topY[cell] + 1.02f;
                    vertices.vertex(matrix, x, y, z).color(r, g, b, 255);
                    vertices.vertex(matrix, x, y, z + 4f).color(r, g, b, 255);
                    vertices.vertex(matrix, x + 4f, y, z + 4f).color(r, g, b, 255);
                    vertices.vertex(matrix, x + 4f, y, z).color(r, g, b, 255);
                    if (++rendered >= MAX_RENDER_TILES) break;
                }
                if (rendered >= MAX_RENDER_TILES) break;
            }
            if (rendered >= MAX_RENDER_TILES) break;
        }
        if (rendered > 0) buffers.draw(RenderLayer.getDebugQuads());
        matrices.pop();
    }
}
