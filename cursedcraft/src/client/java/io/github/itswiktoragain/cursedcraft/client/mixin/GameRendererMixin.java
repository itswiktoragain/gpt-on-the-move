package io.github.itswiktoragain.cursedcraft.client.mixin;

import io.github.itswiktoragain.cursedcraft.client.CursedCraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void cursedcraft$unstablePerspective(Camera camera, float delta, boolean changing, CallbackInfoReturnable<Float> cir) {
        float intensity = CursedCraftClient.corruptionIntensity();
        if (intensity > 0) {
            double t = System.nanoTime() / 100000000.0;
            cir.setReturnValue(cir.getReturnValue() + (float)(Math.sin(t * 7) * 14 * intensity
                    + Math.sin(t * 47) * 7 * intensity));
        }
    }
}
