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
 * in world space, using the original throw direction with no rotation over time.
 * No velocity-linked banking or pitching, as requested.
 */
public final class DiscFlightRenderer extends ThrownItemRenderer<DiscEntity> {
    // The ring spans 14/16 of a full block in the model. At 0.5 scale its
    // real diameter is 0.4375 blocks, matching the 0.5-block projectile hitbox.
    // Promotional close-ups are not representative of world-space size.
    // The raw 3D model's GROUND display transform is 0.28 for dropped items.
    // Thrown projectiles also use GROUND context, so compensate for that
    // transform to retain their effective 0.5x world-space scale.
    private static final float PROJECTILE_SCALE = 0.5F;
    private static final float GROUND_ITEM_SCALE = 0.28F;

    public DiscFlightRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** The renderer has its own state so the initial throw heading survives the
     * extraction-to-render handoff in Minecraft 26.3. */
    public static final class FlightState extends ThrownItemRenderState {
        public float throwYaw;
    }

    @Override
    public FlightState createRenderState() {
        return new FlightState();
    }

    @Override
    public void extractRenderState(DiscEntity entity, ThrownItemRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        ((FlightState) state).throwYaw = entity.getThrowYaw();
    }

    @Override
    public void submit(ThrownItemRenderState state, PoseStack poseStack,
                       SubmitNodeCollector nodes, CameraRenderState camera) {
        poseStack.pushPose();
        // Player yaw is zero towards world +Z (south), positive clockwise
        // towards -X. JOML's Y rotation has the opposite sign, hence -yaw.
        // This is the stored launch yaw, NOT the changing velocity heading.
        // A fixed X rotation lays the ring horizontally (XZ plane).
        Quaternionf orientation = new Quaternionf()
            .rotationY((float) Math.toRadians(-((FlightState) state).throwYaw))
            .rotateX((float) (Math.PI * 0.5));
        poseStack.rotate(orientation);
        float adjusted = PROJECTILE_SCALE / GROUND_ITEM_SCALE;
        poseStack.scale(adjusted, adjusted, adjusted);

        // Vanilla extraction puts this projectile's item in GROUND context,
        // selecting the voxel-extruded 3D model rather than the inventory icon.
        // The adjusted renderer scaling cancels the smaller dropped-item transform.
        state.item.submit(poseStack, nodes, state.lightCoords,
            OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
