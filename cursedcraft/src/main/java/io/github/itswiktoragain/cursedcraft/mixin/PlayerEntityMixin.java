package io.github.itswiktoragain.cursedcraft.mixin;

import io.github.itswiktoragain.cursedcraft.LegProgress;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements LegProgress {
    @Unique private static final TrackedData<Float> CURSEDCRAFT_LEGS =
            DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.FLOAT);

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void cursedcraft$trackLegs(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(CURSEDCRAFT_LEGS, 1.0f);
    }

    @Override
    public float cursedcraft$getLegScale() {
        return ((PlayerEntity)(Object)this).getDataTracker().get(CURSEDCRAFT_LEGS);
    }

    @Override
    public void cursedcraft$setLegScale(float scale) {
        ((PlayerEntity)(Object)this).getDataTracker().set(
                CURSEDCRAFT_LEGS, Math.max(0.0f, Math.min(1.0f, scale)));
    }
}
