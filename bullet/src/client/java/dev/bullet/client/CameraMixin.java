package dev.bullet.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void bullet$shakeCamera(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                    boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!BulletClient.isAnimating() || focusedEntity != MinecraftClient.getInstance().player) return;
        double shake = BulletClient.shakeAmount(tickDelta);
        if (shake <= 0.0001) return;
        Camera camera = (Camera) (Object) this;
        double t = BulletClient.getAnimationTick() + tickDelta;
        float yaw = camera.getYaw() + (float) ((Math.sin(t * 3.17) + Math.cos(t * 5.77)) * 3.6 * shake);
        float pitch = camera.getPitch() + (float) ((Math.sin(t * 4.33) - Math.cos(t * 2.31)) * 2.8 * shake);
        setRotation(yaw, pitch);
    }
}
