package dev.bullet.client;

import dev.bullet.BulletEntity;
import dev.bullet.BulletMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class BulletClient implements ClientModInitializer {
    private static int animationTick = -1;
    private static int lastVehicleId = Integer.MIN_VALUE;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(BulletMod.BULLET_ENTITY, BulletEntityRenderer::new);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> System.out.println("[Bullet] BULLET_CLIENT_READY"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                animationTick = -1;
                lastVehicleId = Integer.MIN_VALUE;
                return;
            }
            if (animationTick >= 0 && ++animationTick > 115) {
                animationTick = -1;
            }
            if (client.player.getVehicle() instanceof BulletEntity bullet) {
                if (lastVehicleId != bullet.getId() && animationTick < 0 && client.currentScreen == null) {
                    lastVehicleId = bullet.getId();
                    client.setScreen(new BulletDestinationScreen());
                } else {
                    lastVehicleId = bullet.getId();
                }
            } else if (animationTick < 0) {
                lastVehicleId = Integer.MIN_VALUE;
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (animationTick < 0) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            int width = drawContext.getScaledWindowWidth();
            int height = drawContext.getScaledWindowHeight();
            int fov = (int) Math.round(effectiveFov(0));
            String phase = animationTick < BulletEntity.LAUNCH_TICKS ? "CHARGING" : "ARRIVED";
            drawContext.drawCenteredTextWithShadow(client.textRenderer,
                "BULLET  /  " + phase, width / 2, height / 2 - 40, 0xffe59b);
            drawContext.drawCenteredTextWithShadow(client.textRenderer,
                "FOV  " + fov + "°", width / 2, height - 55, 0xffffff);
        });
    }

    public static void beginLaunch() {
        animationTick = 0;
    }

    public static boolean isAnimating() {
        return animationTick >= 0;
    }

    public static int getAnimationTick() {
        return animationTick;
    }

    // The logical FOV intentionally goes well past Quake Pro to 720 degrees.
    public static double effectiveFov(float tickDelta) {
        if (animationTick < 0) return Double.NaN;
        double t = animationTick + tickDelta;
        double p;
        if (t <= BulletEntity.LAUNCH_TICKS) {
            p = Math.min(1.0, t / BulletEntity.LAUNCH_TICKS);
            p = p * p * (3.0 - 2.0 * p);
        } else {
            p = Math.min(1.0, (t - BulletEntity.LAUNCH_TICKS) / 60.0);
            p = 1.0 - p * p * (3.0 - 2.0 * p);
        }
        return 90.0 + 630.0 * p;
    }

    public static double shakeAmount(float tickDelta) {
        if (animationTick < 0) return 0;
        double fov = effectiveFov(tickDelta);
        return Math.max(0, (fov - 90.0) / 630.0);
    }
}
