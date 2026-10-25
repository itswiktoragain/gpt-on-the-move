package io.github.itswiktoragain.cursedcraft.client;

import io.github.itswiktoragain.cursedcraft.CursedCraft;
import io.github.itswiktoragain.cursedcraft.MannequinEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import io.github.itswiktoragain.cursedcraft.LegProgress;
import net.minecraft.client.option.Perspective;

public final class CursedCraftClient implements ClientModInitializer {
    public static long deathVisualStart = 0;
    private static boolean warningShown = false;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(CursedCraft.MANNEQUIN, MannequinRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!warningShown && client.currentScreen instanceof TitleScreen screen) {
                warningShown = true;
                client.setScreen(new ThrowawayWarningScreen(screen));
                return;
            }
            if (client.player == null || !client.isInSingleplayer()) {
                deathVisualStart = 0;
                return;
            }
            if ((!client.player.isAlive() || ((LegProgress)client.player).cursedcraft$getLegScale() <= 0) && deathVisualStart == 0) {
                deathVisualStart = System.nanoTime();
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
            if (deathVisualStart != 0 && client.currentScreen instanceof DeathScreen) {
                client.setScreen(null);
            }
        });
    }

    public static float corruptionIntensity() {
        if (deathVisualStart == 0) return 0;
        return Math.min(1f, (System.nanoTime() - deathVisualStart) / 5_000_000_000f);
    }
}
