package dev.wiktor.unboundhorizons.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avoid damage from the below-world handler. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "tickInVoid", at = @At("HEAD"), cancellable = true, require = 0)
    private void unboundhorizons$ignoreLivingVoid(CallbackInfo ci) {
        ci.cancel();
    }
}
