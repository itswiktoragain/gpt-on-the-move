package dev.bullet;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class BulletEntity extends Entity {
    public static final int LAUNCH_TICKS = 55;

    private int countdown;
    private Vec3d destination;

    public BulletEntity(EntityType<? extends BulletEntity> type, World world) {
        super(type, world);
        this.noClip = false;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        // The client animation is local; navigation and destination remain server-authoritative.
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        // Cancel unfinished launches when the entity is reloaded.
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) { }

    @Override
    public void tick() {
        super.tick();
        setVelocity(Vec3d.ZERO);
        if (!getWorld().isClient && countdown > 0) {
            countdown--;
            if (countdown == 0) {
                completeFlight();
            }
        }
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.isSneaking() && !hasPassengers()) {
            if (!getWorld().isClient) {
                player.getInventory().offerOrDrop(new ItemStack(BulletMod.BULLET_ITEM));
                discard();
            }
            return ActionResult.SUCCESS;
        }
        if (!getWorld().isClient && !hasPassengers()) {
            player.startRiding(this, true);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().isEmpty();
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        updater.accept(passenger, getX(), getY() + 0.58, getZ());
    }

    @Override
    public boolean canHit() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    public boolean setDestination(double x, double y, double z) {
        if (countdown != 0 || getFirstPassenger() == null) return false;
        destination = new Vec3d(x, y, z);
        countdown = LAUNCH_TICKS;
        getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP,
                             SoundCategory.PLAYERS, 0.65f, 1.5f);
        return true;
    }

    private void completeFlight() {
        if (!(getWorld() instanceof ServerWorld serverWorld)) return;
        if (!(getFirstPassenger() instanceof ServerPlayerEntity player) || destination == null) return;

        double x = destination.x;
        double y = destination.y;
        double z = destination.z;
        destination = null;

        // Load/generate target terrain *before* moving either entity.
        serverWorld.getChunk(MathHelper.floor(x) >> 4, MathHelper.floor(z) >> 4);

        // Move both the vehicle and its rider, and reattach at the destination.
        player.stopRiding();
        refreshPositionAndAngles(x, y, z, getYaw(), getPitch());
        player.requestTeleport(x, y + 0.58, z);
        player.startRiding(this, true);

        serverWorld.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                              SoundCategory.PLAYERS, 1.2f, 0.6f);
    }
}
