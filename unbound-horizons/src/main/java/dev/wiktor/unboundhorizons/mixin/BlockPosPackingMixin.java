package dev.wiktor.unboundhorizons.mixin;

import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-allocate the packed BlockPos 64-bit budget:
 *
 * vanilla: 26 X + 12 Y + 26 Z = 64
 * extended: 25 X + 14 Y + 25 Z = 64
 *
 * The full vertical signed packed range is -8192..8191. All of BlockPos's
 * pack/unpack methods read these computed static fields. DimensionType's
 * legal min Y and height are derived from BlockPos.SIZE_BITS_Y as well.
 *
 * IMPORTANT: X/Z are now limited to +/-16,777,216 (not +/-33,554,432).
 * Using out-of-range coordinates would cause packed position collisions.
 * The server constrains its world border accordingly.
 */
@Mixin(BlockPos.class)
public abstract class BlockPosPackingMixin {
    @Shadow @Final @Mutable public static int SIZE_BITS_XZ;
    @Shadow @Final @Mutable public static int SIZE_BITS_Y;

    @Shadow @Final @Mutable private static long BITS_X;
    @Shadow @Final @Mutable private static long BITS_Y;
    @Shadow @Final @Mutable private static long BITS_Z;
    @Shadow @Final @Mutable private static int BIT_SHIFT_Z;
    @Shadow @Final @Mutable private static int BIT_SHIFT_X;
    @Shadow @Final @Mutable public static int MAX_XZ;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void unboundhorizons$expandPackedY(CallbackInfo ci) {
        SIZE_BITS_XZ = 25;
        SIZE_BITS_Y = 14;
        BITS_X = (1L << SIZE_BITS_XZ) - 1L;
        BITS_Y = (1L << SIZE_BITS_Y) - 1L;
        BITS_Z = (1L << SIZE_BITS_XZ) - 1L;
        BIT_SHIFT_Z = SIZE_BITS_Y;
        BIT_SHIFT_X = SIZE_BITS_Y + SIZE_BITS_XZ;
        MAX_XZ = (1 << (SIZE_BITS_XZ - 1)) - 1;
    }
}
