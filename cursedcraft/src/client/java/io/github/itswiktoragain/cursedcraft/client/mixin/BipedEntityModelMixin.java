package io.github.itswiktoragain.cursedcraft.client.mixin;

import io.github.itswiktoragain.cursedcraft.LegProgress;
import io.github.itswiktoragain.cursedcraft.client.SkinCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {
    @Inject(method = "setAngles", at = @At("TAIL"))
    private void cursedcraft$animate(BipedEntityRenderState state, CallbackInfo ci) {
        BipedEntityModel<?> model = (BipedEntityModel<?>)(Object)this;
        if (state instanceof PlayerEntityRenderState playerState) {
            var client = MinecraftClient.getInstance();
            float s = 1f;
            if (client.world != null) {
                var player = client.world.getEntityById(playerState.id);
                if (player instanceof LegProgress progress) s = progress.cursedcraft$getLegScale();
            }
            model.leftLeg.yScale = Math.max(0.0001f, s);
            model.rightLeg.yScale = Math.max(0.0001f, s);
            return;
        }

        if (state instanceof ZombieEntityRenderState zombieState && SkinCache.isMannequin(zombieState)) {
            float t = state.age;
            float m = SkinCache.isGiant(zombieState) ? .6f : 1.0f;
            // No zombie attack pose. Everything convulses independently.
            model.head.yaw = (float)Math.sin(t * .48f) * 1.45f * m;
            model.head.pitch = (float)Math.cos(t * .83f) * 1.1f * m;
            model.head.roll = (float)Math.sin(t * 1.11f) * .9f * m;
            model.body.pitch = (float)Math.sin(t * .41f) * .75f * m;
            model.body.yaw = (float)Math.cos(t * .73f) * .55f * m;
            model.body.roll = (float)Math.cos(t * .37f) * .8f * m;
            model.leftArm.pitch = (float)Math.sin(t * 2.1f) * 2.7f;
            model.leftArm.yaw = (float)Math.cos(t * 1.4f) * 1.4f;
            model.leftArm.roll = (float)Math.sin(t * 1.65f) * 2.3f;
            model.rightArm.pitch = (float)Math.cos(t * 1.91f) * 2.7f;
            model.rightArm.yaw = (float)Math.sin(t * 1.57f) * 1.4f;
            model.rightArm.roll = (float)Math.cos(t * 1.87f) * 2.3f;
            model.leftLeg.pitch = (float)Math.sin(t * .79f) * 1.7f;
            model.rightLeg.pitch = (float)Math.cos(t * 1.03f) * 1.7f;
        }
    }
}
