package io.github.itswiktoragain.cursedcraft;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
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
    private static final TrackedData<String> SKIN_UUID =
            DataTracker.registerData(MannequinEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> PLAYER_COPY =
            DataTracker.registerData(MannequinEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> GIANT =
            DataTracker.registerData(MannequinEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public MannequinEntity(EntityType<? extends ZombieEntity> type, World world) {
        super(type, world);
        setBaby(false);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN_NAME, "Dream");
        builder.add(SKIN_UUID, "");
        builder.add(PLAYER_COPY, false);
        builder.add(GIANT, false);
    }

    public String getSkinName() { return getDataTracker().get(SKIN_NAME); }
    public String getSkinUuid() { return getDataTracker().get(SKIN_UUID); }
    public boolean isGiant() { return getDataTracker().get(GIANT); }

    public void setIdentity(String skinName, boolean playerCopy) {
        setIdentity(skinName, playerCopy, "");
    }

    public void setIdentity(String skinName, boolean playerCopy, String uuid) {
        getDataTracker().set(SKIN_NAME, skinName);
        getDataTracker().set(SKIN_UUID, uuid == null ? "" : uuid);
        getDataTracker().set(PLAYER_COPY, playerCopy);
        setCustomName(Text.literal(playerCopy ? skinName + " (YOU)" : skinName));
        setCustomNameVisible(true);
    }

    public void setGiant(boolean giant) {
        getDataTracker().set(GIANT, giant);
        if (giant) {
            getAttributeInstance(EntityAttributes.SCALE).setBaseValue(11.0);
            setAiDisabled(true);
            setPersistent();
            setCustomName(Text.literal("§4§l" + getSkinName() + " ???"));
        }
    }

    @Override protected boolean burnsInDaylight() { return false; }
    @Override protected boolean canConvertInWater() { return false; }

    @Override
    public boolean tryAttack(ServerWorld world, Entity target) {
        if (isGiant()) return false;
        boolean hit = super.tryAttack(world, target);
        if (hit && !getDataTracker().get(PLAYER_COPY) && target instanceof ServerPlayerEntity player) {
            CurseController.spawnPlayerCopy(world, player, this);
        }
        return hit;
    }
}
