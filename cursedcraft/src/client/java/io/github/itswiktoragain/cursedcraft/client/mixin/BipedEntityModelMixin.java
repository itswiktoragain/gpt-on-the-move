package io.github.itswiktoragain.cursedcraft.client.mixin;

import io.github.itswiktoragain.cursedcraft.LegProgress;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {
    @Inject(method = "setAngles", at = @At("TAIL"))
    private void cursedcraft$shrinkLegs(BipedEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState playerState)) return;
        var client = MinecraftClient.getInstance();
        float s = 1.0f;
        if (client.world != null) {
            var player = client.world.getEntityById(playerState.id);
            if (player instanceof LegProgress progress) s = progress.cursedcraft$getLegScale();
        }
        BipedEntityModel<?> model = (BipedEntityModel<?>)(Object)this;
        model.leftLeg.yScale = Math.max(0.0001f, s);
        model.rightLeg.yScale = Math.max(0.0001f, s);
    }
}
