package dev.wiktor.unboundhorizons.mixin;

import dev.wiktor.unboundhorizons.vertical.VerticalWorldBorder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-authoritative fallback: after movement, reset any out-of-range Y
 * and cancel velocity pushing further through the lower/upper border.
 *
 * It is intentionally inert in the currently playable -4096..4095 world,
 * and serves as part of the future cubic-height movement integration.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerYBorderMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void unboundhorizons$guardVerticalBorder(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        double y = player.getY();
        if (!Double.isFinite(y)) return;
        double clamped = VerticalWorldBorder.clampPlayerFeetY(y);
        if (clamped != y) {
            player.setPosition(player.getX(), clamped, player.getZ());
        }
        Vec3d motion = player.getVelocity();
        if (clamped <= VerticalWorldBorder.MIN_PLAYABLE_Y && motion.y < 0.0
            || clamped >= VerticalWorldBorder.MAX_PLAYABLE_Y && motion.y > 0.0) {
            player.setVelocity(motion.x, 0.0, motion.z);
        }
    }
}
