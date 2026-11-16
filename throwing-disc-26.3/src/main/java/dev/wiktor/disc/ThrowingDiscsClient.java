package dev.wiktor.disc;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class ThrowingDiscsClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityRenderers.register(ThrowingDiscs.DISC, DiscFlightRenderer::new);
    }
}