package dev.wiktor.unboundhorizons.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access the original int y stored in the private SerializedChunk.SectionData record. */
@Mixin(targets = "net.minecraft.world.chunk.SerializedChunk$SectionData")
public interface SectionDataYAccessor {
    @Accessor("y")
    int unboundhorizons$getY();
}
