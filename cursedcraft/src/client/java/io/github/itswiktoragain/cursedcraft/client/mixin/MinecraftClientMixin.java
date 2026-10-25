package io.github.itswiktoragain.cursedcraft.client.mixin;

import io.github.itswiktoragain.cursedcraft.client.CursedCraftClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.Perspective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void cursedcraft$skipDeathScreen(Screen screen, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient)(Object)this;
        if (screen instanceof DeathScreen && client.isInSingleplayer()) {
            if (CursedCraftClient.deathVisualStart == 0)
                CursedCraftClient.deathVisualStart = System.nanoTime();
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            ci.cancel();
        }
    }
}
