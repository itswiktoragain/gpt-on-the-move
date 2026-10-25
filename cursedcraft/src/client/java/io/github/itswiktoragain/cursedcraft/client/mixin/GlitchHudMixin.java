package io.github.itswiktoragain.cursedcraft.client.mixin;

import io.github.itswiktoragain.cursedcraft.client.CursedCraftClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class GlitchHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void cursedcraft$drawSignalCorruption(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        float strength = CursedCraftClient.corruptionIntensity();
        if (strength < .05f) return;
        var window = MinecraftClient.getInstance().getWindow();
        int w = window.getScaledWidth(), h = window.getScaledHeight();
        if (w <= 0 || h <= 0) return;
        long now = System.nanoTime() / 35_000_000L;
        java.util.Random random = new java.util.Random(now * 7307L);
        int strips = 1 + (int)(strength * 15);
        for (int i = 0; i < strips; i++) {
            int y = random.nextInt(h);
            int thickness = 1 + random.nextInt(Math.max(1, 2 + (int)(strength * 9)));
            int x = random.nextInt(w);
            int color = (random.nextBoolean() ? 0x54F00055 : 0x5600FFDD);
            context.fill(0, y, w, Math.min(h, y + thickness), color);
            context.fill(x, y, Math.min(w, x + random.nextInt(Math.max(1, w / 2))),
                    Math.min(h, y + thickness * 2), 0x42FFFFFF);
        }
    }
}
