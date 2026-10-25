package dev.bullet.client;

import dev.bullet.BulletEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

public final class BulletEntityRenderer extends EntityRenderer<BulletEntity, EntityRenderState> {
    public BulletEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    private void cube(BlockState material, MatrixStack matrices, VertexConsumerProvider consumers,
                      int light, float x, float y, float z, float sx, float sy, float sz) {
        matrices.push();
        matrices.translate(x, y, z);
        matrices.scale(sx, sy, sz);
        MinecraftClient.getInstance().getBlockRenderManager()
            .renderBlockAsEntity(material, matrices, consumers, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }

    @Override
    public void render(EntityRenderState state, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light) {
        matrices.push();
        // A low, streamlined copper-and-iron hovercraft with tail fins and front nozzle.
        cube(Blocks.WAXED_COPPER_BLOCK.getDefaultState(), matrices, consumers, light,
            -0.36f, 0.15f, -0.78f, 0.72f, 0.34f, 1.60f);
        cube(Blocks.IRON_BLOCK.getDefaultState(), matrices, consumers, light,
            -0.44f, 0.20f, -1.07f, 0.88f, 0.23f, 0.42f);
        cube(Blocks.OBSIDIAN.getDefaultState(), matrices, consumers, light,
            -0.26f, 0.48f, -0.17f, 0.52f, 0.13f, 0.53f);
        cube(Blocks.IRON_BLOCK.getDefaultState(), matrices, consumers, light,
            -0.87f, 0.11f, 0.06f, 1.74f, 0.12f, 0.40f);
        cube(Blocks.IRON_BLOCK.getDefaultState(), matrices, consumers, light,
            -0.38f, 0.38f, 0.62f, 0.14f, 0.48f, 0.26f);
        cube(Blocks.IRON_BLOCK.getDefaultState(), matrices, consumers, light,
             0.24f, 0.38f, 0.62f, 0.14f, 0.48f, 0.26f);
        cube(Blocks.REDSTONE_BLOCK.getDefaultState(), matrices, consumers, light,
            -0.20f, 0.23f, 0.84f, 0.40f, 0.19f, 0.13f);
        matrices.pop();
        super.render(state, matrices, consumers, light);
    }
}
