package dev.wiktor.unboundhorizons.mixin;

import dev.wiktor.unboundhorizons.UnboundHorizons;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's deep aquifer fallback turns open caves into lava below
 * approximately -54, which turns all air pockets into lava when the
 * world generation range is extended far below the vanilla minimum.
 *
 * Use ordinary air ONLY in underground empty noise cells below -64.
 * Rock stays rock, and explicit player-placed lava is not intercepted.
 * Applies to the full aquifer implementation, not other dimensions'
 * sea-level implementations.
 */
@Mixin(targets = "net.minecraft.world.gen.chunk.AquiferSampler$Impl")
public abstract class DeepAquiferMixin {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void unboundhorizons$keepDeepCavesAir(
        DensityFunction.NoisePos position,
        double density,
        CallbackInfoReturnable<BlockState> cir
    ) {
        if (position.blockY() < -64 && density <= 0.0D) {
            if (UnboundHorizons.VERIFY_TERRAIN) {
                UnboundHorizons.DEEP_AIR_SAMPLES.incrementAndGet();
            }
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }
}
