package dev.wiktor.disc;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Dedicated renderer for discs in flight. Unlike vanilla ThrownItemRenderer,
 * it never billboard-faces the camera: it renders a proper 3D ring horizontally
 * in world space, aligned to the pixel axes with no rotation over time.
 * No velocity-linked banking or pitching, as requested.
 */
public final class DiscFlightRenderer extends ThrownItemRenderer<DiscEntity> {
    private static final float PROJECTILE_SCALE = 1.1F;

    public DiscFlightRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(ThrownItemRenderState state, PoseStack poseStack,
                       SubmitNodeCollector nodes, CameraRenderState camera) {
        poseStack.pushPose();
        // The original item art faces the XY plane, so the fixed 90-degree
        // X rotation makes its face parallel with the ground (XZ plane).
        // Keep the ring square to Minecraft's world axes. In particular, don't
        // rotate with velocity or animate the heading: fractional-angle rotation
        // makes a pixel-art outline shimmer and appear off-grid.
        Quaternionf orientation = new Quaternionf()
            .rotationX((float) (Math.PI * 0.5));
        poseStack.rotate(orientation);
        poseStack.scale(PROJECTILE_SCALE, PROJECTILE_SCALE, PROJECTILE_SCALE);

        // Vanilla extraction puts this projectile's item in GROUND context,
        // selecting the voxel-extruded 3D model rather than the inventory icon.
        state.item.submit(poseStack, nodes, state.lightCoords,
            OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
