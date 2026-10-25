package dev.bullet.client;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void bullet$boostFov(Camera camera, float tickDelta, boolean changingFov,
                                 CallbackInfoReturnable<Double> cir) {
        double fov = BulletClient.effectiveFov(tickDelta);
        if (!Double.isNaN(fov)) {
            cir.setReturnValue(fov);
        }
    }
}
