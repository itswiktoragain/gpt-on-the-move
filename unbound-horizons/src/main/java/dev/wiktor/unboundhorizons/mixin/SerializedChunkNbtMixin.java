package dev.wiktor.unboundhorizons.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.chunk.SerializedChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla 1.21.4 saves each vertical chunk section's Y as a *signed byte*.
 * That was sufficient for -2032..+2031 but wraps at -4096.
 *
 * We use an integer tag for section Y and read both the new integer tags and
 * old vanilla byte tags with NbtCompound.getInt("Y").
 * The integer is required to distinguish section -188 from +68.
 */
@Mixin(SerializedChunk.class)
public abstract class SerializedChunkNbtMixin {
    /**
     * fromNbt local 29: section's NbtCompound.
     * fromNbt local 30: the section Y integer, immediately after getByte("Y").
     */
    @ModifyVariable(method = "fromNbt", at = @At(value = "STORE"), index = 30)
    private static int unboundhorizons$readIntegerSectionY(
        int vanillaByteY, @Local(index = 29) NbtCompound section
    ) {
        return section.getInt("Y");
    }

    /**
     * serialize local 5: SerializedChunk.SectionData; access its private
     * record's int y field via a strongly typed Mixin accessor.
     */
    @Redirect(
        method = "serialize",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/nbt/NbtCompound;putByte(Ljava/lang/String;B)V")
    )
    private void unboundhorizons$writeIntegerSectionY(
        NbtCompound sectionNbt, String tag, byte wrappedVanillaValue,
        @Local(index = 5) Object sectionData
    ) {
        sectionNbt.putInt(tag, ((SectionDataYAccessor)sectionData).unboundhorizons$getY());
    }
}
