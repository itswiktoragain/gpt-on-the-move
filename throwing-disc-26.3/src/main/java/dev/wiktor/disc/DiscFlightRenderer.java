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
 * in world space with a continuous spin around the global vertical axis.
 * No velocity-linked banking or pitching, as requested.
 */
public final class DiscFlightRenderer extends ThrownItemRenderer<DiscEntity> {
    private static final float SPIN_DEGREES_PER_TICK = 36.0F;
    private static final float PROJECTILE_SCALE = 0.85F;

    public DiscFlightRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(ThrownItemRenderState state, PoseStack poseStack,
                       SubmitNodeCollector nodes, CameraRenderState camera) {
        poseStack.pushPose();
        // The original item art faces the XY plane, so the fixed 90-degree
        // X rotation makes its face parallel with the ground (XZ plane).
        // Rotate around Y in world space first, for a level Frisbee-like spin.
        Quaternionf orientation = new Quaternionf()
            .rotationY((float) Math.toRadians(state.ageInTicks * SPIN_DEGREES_PER_TICK))
            .rotateX((float) (Math.PI * 0.5));
        poseStack.rotate(orientation);
        poseStack.scale(PROJECTILE_SCALE, PROJECTILE_SCALE, PROJECTILE_SCALE);

        // Vanilla extraction puts this projectile's item in GROUND context,
        // selecting the voxel-extruded 3D model rather than the inventory icon.
        state.item.submit(poseStack, nodes, state.lightCoords,
            OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
