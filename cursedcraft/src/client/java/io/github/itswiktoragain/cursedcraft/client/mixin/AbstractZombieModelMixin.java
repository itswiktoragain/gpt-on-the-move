package io.github.itswiktoragain.cursedcraft.client.mixin;

import io.github.itswiktoragain.cursedcraft.client.SkinCache;
import net.minecraft.client.render.entity.model.AbstractZombieModel;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractZombieModel.class)
public abstract class AbstractZombieModelMixin {
    // This hook comes AFTER vanilla meleeAttack(). A BipedEntityModel hook alone
    // would be overwritten by vanilla zombie arm poses.
    @Inject(method = "setAngles", at = @At("TAIL"))
    private void cursedcraft$finalMannequinPose(ZombieEntityRenderState state, CallbackInfo ci) {
        if (!SkinCache.isMannequin(state)) return;
        AbstractZombieModel<?> m = (AbstractZombieModel<?>)(Object)this;
        float t = state.age;
        float f = SkinCache.isGiant(state) ? .75f : 1.0f;
        m.head.yaw = (float)Math.sin(t * .47) * 1.45f * f;
        m.head.pitch = (float)Math.cos(t * .83) * 1.30f * f;
        m.head.roll = (float)Math.sin(t * 1.11) * 1.06f * f;
        m.body.pitch = (float)Math.sin(t * .39) * 1.1f * f;
        m.body.roll = (float)Math.cos(t * .64) * 1.25f * f;
        m.body.yaw = (float)Math.sin(t * .33) * .65f * f;
        m.leftArm.pitch = (float)Math.sin(t * 2.2) * 2.8f;
        m.leftArm.roll = (float)Math.cos(t * 1.8) * 2.5f;
        m.leftArm.yaw = (float)Math.sin(t * .91) * 1.2f;
        m.rightArm.pitch = (float)Math.cos(t * 1.95) * 2.8f;
        m.rightArm.roll = (float)Math.sin(t * 1.62) * 2.5f;
        m.rightArm.yaw = (float)Math.cos(t * 1.11) * 1.2f;
        m.leftLeg.pitch = (float)Math.sin(t * .8) * 1.55f;
        m.rightLeg.pitch = (float)Math.cos(t * 1.04) * 1.55f;
    }
}
