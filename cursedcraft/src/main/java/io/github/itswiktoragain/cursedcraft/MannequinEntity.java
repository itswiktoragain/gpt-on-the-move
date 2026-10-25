package io.github.itswiktoragain.cursedcraft;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public final class MannequinEntity extends ZombieEntity {
    private static final TrackedData<String> SKIN_NAME =
            DataTracker.registerData(MannequinEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> PLAYER_COPY =
            DataTracker.registerData(MannequinEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public MannequinEntity(EntityType<? extends ZombieEntity> type, World world) {
        super(type, world);
        setBaby(false);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN_NAME, "Dream");
        builder.add(PLAYER_COPY, false);
    }

    public String getSkinName() {
        return getDataTracker().get(SKIN_NAME);
    }

    public void setIdentity(String skinName, boolean playerCopy) {
        getDataTracker().set(SKIN_NAME, skinName);
        getDataTracker().set(PLAYER_COPY, playerCopy);
        setCustomName(Text.literal(playerCopy ? skinName + " (you)" : skinName));
        setCustomNameVisible(true);
    }

    @Override
    protected boolean burnsInDaylight() {
        return false;
    }

    @Override
    protected boolean canConvertInWater() {
        return false;
    }

    @Override
    public boolean tryAttack(ServerWorld world, Entity target) {
        boolean hit = super.tryAttack(world, target);
        if (hit && !getDataTracker().get(PLAYER_COPY) && target instanceof ServerPlayerEntity player) {
            CurseController.spawnPlayerCopy(world, player, this);
        }
        return hit;
    }
}
