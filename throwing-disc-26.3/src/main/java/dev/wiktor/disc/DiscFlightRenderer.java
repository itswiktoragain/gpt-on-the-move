package dev.wiktor.disc;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.joml.Matrix4f;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Dedicated renderer for discs in flight. Unlike vanilla ThrownItemRenderer,
 * it never billboard-faces the camera: it renders a proper 3D ring horizontally
 * in world space, using the original throw direction with no rotation over time.
 * No velocity-linked banking or pitching, as requested.
 */
public final class DiscFlightRenderer extends ThrownItemRenderer<DiscEntity> {
    // Render the thrown model independently from dropped-item scaling.
    // The visible voxel ring spans 14/16 model units, giving a disc about
    // 0.63 blocks across in flight, versus 0.79 blocks when held.
    private static final float PROJECTILE_SCALE = 0.72F;
    private final ItemModelResolver itemModelResolver;

    public DiscFlightRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
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
        // Do NOT use GROUND for projectiles: it also controls the size of
        // real dropped ItemEntity objects. NONE picks our dedicated 3D branch
        // without applying the dropped-item scale. The item resolver resets
        // and rebuilds the render state for this context.
        this.itemModelResolver.updateForNonLiving(
            state.item, entity.getItem(), ItemDisplayContext.NONE, entity);
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
        // Both Minecraft 26.2 and 26.3 expose mulPose(Matrix4fc).
        // 26.2's quaternion method is mulPose(Quaternionfc), while 26.3
        // renamed that quaternion overload to rotate(Quaternionfc).
        // Multiplying a pure rotation matrix avoids that binary API mismatch.
        poseStack.mulPose(new Matrix4f().rotation(orientation));
        poseStack.scale(PROJECTILE_SCALE, PROJECTILE_SCALE, PROJECTILE_SCALE);

        // Item extraction selected NONE, mapped to the voxel-extruded
        // projectile model. Dropped items still use their own GROUND transform.
        state.item.submit(poseStack, nodes, state.lightCoords,
            OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
