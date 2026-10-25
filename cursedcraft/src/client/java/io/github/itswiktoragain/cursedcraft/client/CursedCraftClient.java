package io.github.itswiktoragain.cursedcraft.client;

import io.github.itswiktoragain.cursedcraft.CursedCraft;
import io.github.itswiktoragain.cursedcraft.LegProgress;
import io.github.itswiktoragain.cursedcraft.MannequinEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;

public final class CursedCraftClient implements ClientModInitializer {
    public static long deathVisualStart = 0L;
    public static float giantVisualIntensity = 0.0f;
    private static boolean warningShown = false;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(CursedCraft.MANNEQUIN, MannequinRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!warningShown && client.currentScreen instanceof TitleScreen title) {
                warningShown = true;
                client.setScreen(new ThrowawayWarningScreen(title));
                return;
            }
            if (client.player == null || !client.isInSingleplayer()) {
                deathVisualStart = 0;
                giantVisualIntensity = 0;
                return;
            }
            if ((!client.player.isAlive() || ((LegProgress)client.player).cursedcraft$getLegScale() <= 0)
                    && deathVisualStart == 0) {
                deathVisualStart = System.nanoTime();
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
            if (client.currentScreen instanceof DeathScreen) {
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                client.setScreen(null);
            }
            float near = 0f;
            if (client.world != null) {
                var player = client.player;
                var giants = client.world.getEntitiesByClass(MannequinEntity.class,
                        player.getBoundingBox().expand(170), MannequinEntity::isGiant);
                for (MannequinEntity giant : giants) {
                    double distance = giant.getPos().distanceTo(player.getPos());
                    near = Math.max(near, (float)Math.max(0, (150.0 - distance) / 150.0));
                }
            }
            giantVisualIntensity = near;
        });
    }

    public static float corruptionIntensity() {
        float death = deathVisualStart == 0 ? 0
                : Math.min(1f, (System.nanoTime() - deathVisualStart) / 5_000_000_000f);
        return Math.max(death, giantVisualIntensity);
    }
}
