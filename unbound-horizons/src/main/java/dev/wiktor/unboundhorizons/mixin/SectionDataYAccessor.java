package dev.wiktor.unboundhorizons.mixin;

import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.chunk.SerializedChunk$SectionData")
public interface SectionDataYAccessor {
    @Accessor("y")
    int unboundhorizons$getY();

    @Accessor("chunkSection")
    ChunkSection unboundhorizons$getSection();

    @Accessor("blockLight")
    ChunkNibbleArray unboundhorizons$getBlockLight();

    @Accessor("skyLight")
    ChunkNibbleArray unboundhorizons$getSkyLight();
}
