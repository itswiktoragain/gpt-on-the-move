package dev.bullet.client;

import dev.bullet.BulletEntity;
import dev.bullet.BulletMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import java.lang.reflect.Method;
import net.minecraft.client.gui.DrawContext;

public final class BulletClient implements ClientModInitializer {
    private static int animationTick = -1;
    private static int lastVehicleId = Integer.MIN_VALUE;
    private static boolean menuLogged = false;
    private static int menuScreenTicks = 0;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(BulletMod.BULLET_ENTITY, BulletEntityRenderer::new);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> System.out.println("[Bullet] BULLET_CLIENT_READY"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Report *after* the full client renderer initializes and the menu is ticking.
            // CLIENT_STARTED is too early: GameRenderer can still crash after it fires.
            if (!menuLogged && client.currentScreen != null && ++menuScreenTicks >= 20) {
                menuLogged = true;
                if (Boolean.getBoolean("bullet.fovSmokeTest")) {
                    verifyFovEffectRuns(client);
                }
                System.out.println("[Bullet] BULLET_MAIN_MENU_READY");
            }
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

    /**
     * CI regression test: invoke Minecraft's actual getFov() while our launch is
     * active, so a Float/Double callback mismatch causes a failing build.
     * This code is dormant in published games unless explicitly enabled.
     */
    private static void verifyFovEffectRuns(MinecraftClient client) {
        int previousTick = animationTick;
        try {
            Method getFov = GameRenderer.class.getDeclaredMethod(
                "getFov", Camera.class, float.class, boolean.class);
            getFov.setAccessible(true);
            for (int tick : new int[] { 0, 14, BulletEntity.LAUNCH_TICKS, 75, 115 }) {
                animationTick = tick;
                Object output = getFov.invoke(client.gameRenderer,
                    client.gameRenderer.getCamera(), 0.0f, true);
                if (!(output instanceof Float actual)) {
                    throw new IllegalStateException("GameRenderer.getFov did not return Float: " + output);
                }
                float expected = (float) effectiveFov(0.0f);
                if (!Float.isFinite(actual) || Math.abs(actual - expected) > 0.125f) {
                    throw new IllegalStateException("FOV regression at tick " + tick
                        + ": expected " + expected + ", got " + actual);
                }
            }
            System.out.println("[Bullet] BULLET_FOV_SMOKE_PASS");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Bullet FOV injection smoke test failed", e);
        } finally {
            animationTick = previousTick;
        }
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
