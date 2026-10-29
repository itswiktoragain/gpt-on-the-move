package dev.wiktor.unboundhorizons.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.chunk.ChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Replace newly written bedrock with mineable deepslate in every dimension. */
@Mixin(ChunkSection.class)
public abstract class ChunkSectionMixin {
    @ModifyVariable(
        method = "setBlockState(IIILnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;",
        at = @At("HEAD"),
        argsOnly = true,
        index = 4
    )
    private BlockState unboundhorizons$replaceBedrock(BlockState original) {
        return original.isOf(Blocks.BEDROCK) ? Blocks.DEEPSLATE.getDefaultState() : original;
    }
}
