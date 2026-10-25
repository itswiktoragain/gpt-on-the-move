package io.github.itswiktoragain.cursedcraft.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VerticalBlockSample.class)
public interface VerticalBlockSampleAccessor {
    @Accessor("states")
    BlockState[] cursedcraft$getStates();

    @Accessor("startY")
    int cursedcraft$getStartY();
}
