package dev.bullet.mixin;

import dev.bullet.client.BulletClient;
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
                                 CallbackInfoReturnable<Float> cir) {
        // Match the exact FLOAT return type of Minecraft 1.21.4 GameRenderer.
        // Preserve vanilla FOV outside launch, and keep the perspective matrix
        // below 180 degrees while the exponential logical curve reaches 720.
        cir.setReturnValue(BulletClient.renderedFov(tickDelta, cir.getReturnValueF()));
    }
}
