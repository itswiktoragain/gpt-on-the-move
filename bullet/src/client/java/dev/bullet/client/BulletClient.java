package dev.bullet.client;

import dev.bullet.BulletEntity;
import dev.bullet.BulletMod;
import java.lang.reflect.Method;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;

/**
 * Drives Bullet's launch/recovery camera effect, without any FOV HUD overlay.
 *
 * Perspective cameras cannot directly project 720 degrees: at 180 and 360
 * their tangent-based projection becomes singular. Keep a mathematical 720
 * degree target, and map it to a safe 170-degree maximum for the actual
 * perspective matrix. Both curves accelerate exponentially, frame by frame.
 */
public final class BulletClient implements ClientModInitializer {
    public static final int RECOVERY_TICKS = 80;
    public static final float MAX_RENDER_FOV = 170.0f;
    public static final double PEAK_LOGICAL_FOV = 720.0;

    private static int animationTick = -1;
    private static int lastVehicleId = Integer.MIN_VALUE;
    private static float startFov = 90.0f;
    private static float lastNormalFov = 90.0f;
    private static boolean menuLogged = false;
    private static int menuScreenTicks = 0;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(BulletMod.BULLET_ENTITY, BulletEntityRenderer::new);
        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
            System.out.println("[Bullet] BULLET_CLIENT_READY"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Smoke-test the real Minecraft camera path in CI, not just classloading.
            if (!menuLogged && client.currentScreen != null && ++menuScreenTicks >= 20) {
                menuLogged = true;
                if (Boolean.getBoolean("bullet.fovSmokeTest")) {
                    verifyExponentialFov(client);
                }
                System.out.println("[Bullet] BULLET_MAIN_MENU_READY");
            }

            if (client.player == null) {
                animationTick = -1;
                lastVehicleId = Integer.MIN_VALUE;
                return;
            }

            if (animationTick >= 0 && ++animationTick > totalAnimationTicks()) {
                animationTick = -1;
            }

            if (client.player.getVehicle() instanceof BulletEntity bullet) {
                if (lastVehicleId != bullet.getId()
                        && animationTick < 0 && client.currentScreen == null) {
                    lastVehicleId = bullet.getId();
                    client.setScreen(new BulletDestinationScreen());
                } else {
                    lastVehicleId = bullet.getId();
                }
            } else if (animationTick < 0) {
                lastVehicleId = Integer.MIN_VALUE;
            }
        });
        // Intentionally no HudRenderCallback: no "FOV 720" widget, no overlay.
    }

    public static void beginLaunch() {
        if (Float.isFinite(lastNormalFov)) {
            startFov = Math.min(150.0f, Math.max(40.0f, lastNormalFov));
        }
        animationTick = 0;
    }

    public static int totalAnimationTicks() {
        return BulletEntity.LAUNCH_TICKS + RECOVERY_TICKS;
    }

    public static boolean isAnimating() {
        return animationTick >= 0;
    }

    public static int getAnimationTick() {
        return animationTick;
    }

    private static double progress(float tickDelta) {
        if (animationTick < 0) return 0.0;
        double time = Math.max(0, Math.min(totalAnimationTicks(), animationTick + tickDelta));
        if (time <= BulletEntity.LAUNCH_TICKS) {
            return time / BulletEntity.LAUNCH_TICKS;
        }
        return Math.max(0.0, 1.0 - (time - BulletEntity.LAUNCH_TICKS) / RECOVERY_TICKS);
    }

    /** Real exponential curve: startFov * (720/startFov)^progress. */
    public static double logicalFov(float tickDelta) {
        if (!isAnimating()) return Double.NaN;
        return startFov * Math.pow(PEAK_LOGICAL_FOV / startFov, progress(tickDelta));
    }

    /** The shader-safe projection follows the same exponential progress. */
    public static float renderedFov(float tickDelta, float vanillaFov) {
        if (!isAnimating()) {
            // The main menu sometimes reports a zero camera FOV because no
            // in-world camera is ready. Do not adopt it as the launch baseline.
            if (Float.isFinite(vanillaFov) && vanillaFov >= 30.0f && vanillaFov < 180.0f) {
                lastNormalFov = vanillaFov;
            }
            return vanillaFov;
        }
        // Once launched, use the captured FOV. A missing/uninitialized vanilla
        // camera must not suppress the exponential effect in the regression test.
        double logical = logicalFov(tickDelta);
        double portion = (logical - startFov) / (PEAK_LOGICAL_FOV - startFov);
        portion = Math.max(0.0, Math.min(1.0, portion));
        double display = startFov + (MAX_RENDER_FOV - startFov) * portion;
        return (float) Math.min(MAX_RENDER_FOV, Math.max(startFov, display));
    }

    public static double shakeAmount(float tickDelta) {
        if (!isAnimating()) return 0.0;
        return Math.max(0.0, Math.min(1.0,
            (logicalFov(tickDelta) - startFov) / (PEAK_LOGICAL_FOV - startFov)));
    }

    /**
     * Runs through every frame of the curve using Minecraft's actual getFov
     * method and its real Mixin. This also verifies no sudden initial jump,
     * 720 logical degrees at teleport, and a smooth return to vanilla.
     */
    private static void verifyExponentialFov(MinecraftClient client) {
        int savedTick = animationTick;
        float savedStart = startFov;
        float savedVanilla = lastNormalFov;
        try {
            Method method = GameRenderer.class.getDeclaredMethod(
                "getFov", Camera.class, float.class, boolean.class);
            method.setAccessible(true);
            Camera camera = client.gameRenderer.getCamera();
            animationTick = -1;
            Object normalResult = method.invoke(client.gameRenderer, camera, 0.0f, true);
            if (!(normalResult instanceof Float normal) || !Float.isFinite(normal)) {
                throw new IllegalStateException("Vanilla getFov must return a finite float");
            }
            beginLaunch();

            float previous = Float.NEGATIVE_INFINITY;
            float firstTickFov = Float.NaN;
            for (int t = 0; t <= totalAnimationTicks(); t++) {
                animationTick = t;
                Object result = method.invoke(client.gameRenderer, camera, 0.0f, true);
                if (!(result instanceof Float actual) || !Float.isFinite(actual)
                    || actual <= 0 || actual > MAX_RENDER_FOV + 0.0001f) {
                    throw new IllegalStateException("Unsafe projection at tick " + t + ": " + result);
                }

                float expected = renderedFov(0.0f, normal);
                if (Math.abs(actual - expected) > 0.01f) {
                    throw new IllegalStateException("Wrong FOV at tick " + t
                        + ": expected " + expected + " but found " + actual);
                }
                if (t > 0 && t <= BulletEntity.LAUNCH_TICKS && actual < previous - 0.01f) {
                    throw new IllegalStateException("Launch FOV decreased at tick " + t);
                }
                if (t > BulletEntity.LAUNCH_TICKS && actual > previous + 0.01f) {
                    throw new IllegalStateException("Recovery FOV increased at tick " + t);
                }
                if (t == 1) firstTickFov = actual;
                if (t == BulletEntity.LAUNCH_TICKS
                        && Math.abs(logicalFov(0.0f) - 720.0) > 0.001) {
                    throw new IllegalStateException("Logical FOV did not reach 720");
                }
                previous = actual;
            }
            if (Math.abs(firstTickFov - startFov) > 2.0f) {
                throw new IllegalStateException("FOV jumped instantly after launch");
            }
            if (Math.abs(previous - startFov) > 0.01f) {
                throw new IllegalStateException("FOV did not return to normal");
            }
            System.out.println("[Bullet] BULLET_EXPONENTIAL_FOV_TEST_PASS");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Bullet camera regression test failed", ex);
        } finally {
            animationTick = savedTick;
            startFov = savedStart;
            lastNormalFov = savedVanilla;
        }
    }
}
