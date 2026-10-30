package dev.wiktor.unboundhorizons.mixin;

import dev.wiktor.unboundhorizons.vertical.VerticalWorldBorder;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancel vanilla void damage, and impose a future-proof Y movement limit. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "tickInVoid", at = @At("HEAD"), cancellable = true, require = 0)
    private void unboundhorizons$ignoreVoid(CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * Stops setPosition and teleport flows before they assign a position
     * beyond the 16-block-margin vertical border. Vanilla still has the
     * shorter playable dimension until native cubic chunks are integrated.
     *
     * This guard is a secondary safeguard, not a complete replacement for
     * the movement/physics engine.
     */
    @ModifyVariable(method = "setPosition(DDD)V", at = @At("HEAD"),
        argsOnly = true, index = 3, require = 0)
    private double unboundhorizons$clampVerticalPosition(double y) {
        return Double.isFinite(y)
            ? VerticalWorldBorder.clampPlayerFeetY(y)
            : y;
    }
}
