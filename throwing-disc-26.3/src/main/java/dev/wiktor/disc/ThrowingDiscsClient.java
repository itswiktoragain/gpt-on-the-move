package dev.wiktor.disc;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class ThrowingDiscsClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityRenderers.register(ThrowingDiscs.DISC, ThrownItemRenderer::new);
    }
}