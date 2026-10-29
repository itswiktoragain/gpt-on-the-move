package dev.wiktor.unboundhorizons.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avoid the generic entity discard when below the world's minimum height. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "tickInVoid", at = @At("HEAD"), cancellable = true, require = 0)
    private void unboundhorizons$ignoreVoid(CallbackInfo ci) {
        ci.cancel();
    }
}
