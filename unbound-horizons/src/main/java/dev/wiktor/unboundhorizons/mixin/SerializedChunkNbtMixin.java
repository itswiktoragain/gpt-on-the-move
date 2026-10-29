package dev.wiktor.unboundhorizons.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.chunk.SerializedChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SerializedChunk.class)
public abstract class SerializedChunkNbtMixin {
    @Shadow @Final private List<?> sectionData;

    @ModifyVariable(method = "fromNbt", at = @At(value = "STORE"), index = 30)
    private static int unboundhorizons$readIntegerSectionY(
        int original, @Local(index = 29) NbtCompound nbt
    ) {
        return nbt.getInt("Y");
    }

    @Inject(method = "serialize", at = @At("RETURN"))
    private void unboundhorizons$restoreFullSectionIdentifiers(CallbackInfoReturnable<NbtCompound> ci) {
        NbtList nbtSections = ci.getReturnValue().getList("sections", 10);
        int nbtIndex = 0;
        for (Object raw : this.sectionData) {
            SectionDataYAccessor record = (SectionDataYAccessor)raw;
            if (record.unboundhorizons$getSection() == null
                && record.unboundhorizons$getBlockLight() == null
                && record.unboundhorizons$getSkyLight() == null) continue;
            if (nbtIndex >= nbtSections.size()) {
                throw new IllegalStateException("Too few serialized sections");
            }
            NbtCompound section = nbtSections.getCompound(nbtIndex++);
            int exactY = record.unboundhorizons$getY();
            if (section.getByte("Y") != (byte)exactY) {
                throw new IllegalStateException("Section ordering mismatch at Y=" + exactY);
            }
            section.putInt("Y", exactY);
        }
        if (nbtIndex != nbtSections.size()) {
            throw new IllegalStateException("Unexpected trailing serialized sections");
        }
    }
}
