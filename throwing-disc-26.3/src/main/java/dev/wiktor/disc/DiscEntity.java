package dev.wiktor.disc;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class DiscEntity extends ThrowableItemProjectile {
    private ItemStack payload = ItemStack.EMPTY;
    private boolean creative = false;
    private boolean offHand = false;
    private boolean returning = false;
    private int ricochets = 0;
    private double distance = 0;
    private Vec3 lastPosition;
    private final Set<UUID> struck = new HashSet<>();

    public DiscEntity(EntityType<? extends ThrowableItemProjectile> type, Level world) {
        super(type, world);
        setNoGravity(true);
    }
    public DiscEntity(Level world, LivingEntity owner, ItemStack stack) {
        super(ThrowingDiscs.DISC, owner, world, stack);
        setNoGravity(true);
        payload = stack.copyWithCount(1);
    }
    public DiscEntity(Level world, Player owner, ItemStack stack, DiscItem disc, boolean creative, InteractionHand hand) {
        this(world, owner, stack);
        this.creative = creative;
        this.offHand = hand == InteractionHand.OFF_HAND;
    }
    @Override protected Item getDefaultItem() { return ThrowingDiscs.IRON; }

    private void beginReturn() { returning = true; struck.clear(); setNoGravity(true); }

    @Override public void tick() {
        Vec3 previous = position();
        if (returning && getOwner() instanceof Player player && player.isAlive()) {
            Vec3 target = player.getEyePosition().add(0, -0.38, 0);
            Vec3 toOwner = target.subtract(position());
            if (!level().isClientSide() && toOwner.lengthSqr() < 2.2) {
                deliver(player); return;
            }
            Vec3 desired = toOwner.normalize().scale(1.7 + Math.min(ricochets, 4) * 0.12);
            setDeltaMovement(getDeltaMovement().scale(0.25).add(desired.scale(0.75)));
            noPhysics = true;
        } else {
            noPhysics = false;
        }
        setNoGravity(true);
        super.tick();
        if (!level().isClientSide()) {
            if (!returning) {
                distance += position().distanceTo(previous);
                double max = payload.getItem() instanceof DiscItem d ? d.distance : 28;
                if (distance >= max || tickCount >= 100) beginReturn();
            }
            if (tickCount >= 300 || !(getOwner() instanceof Player owner) || !owner.isAlive()) {
                if (!creative) spawnAtLocation(payload);
                discard();
            }
        }
    }
    private void deliver(Player player) {
        if (!creative && !payload.isEmpty()) {
            ItemStack result = payload.copy();
            if (result.isDamageableItem()) {
                int updated = result.getDamageValue() + 1;
                if (updated >= result.getMaxDamage()) { discard(); return; }
                result.setDamageValue(updated);
            }
            InteractionHand hand = offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (player.getItemInHand(hand).isEmpty()) player.setItemInHand(hand, result);
            else if (!player.getInventory().add(result)) player.drop(result, false);
        }
        discard();
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        if (returning) return;
        ricochets++;
        if (ricochets > 3) { beginReturn(); return; }
        Vec3 v = getDeltaMovement();
        Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i());
        Vec3 reflected = v.subtract(n.scale(2 * v.dot(n)));
        setDeltaMovement(reflected.scale(1.18).add(n.scale(0.06)));
        setPos(getX()+n.x*0.12, getY()+n.y*0.12, getZ()+n.z*0.12);
        struck.clear();
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        Entity victim = hit.getEntity();
        if (!(victim instanceof LivingEntity living) || victim == getOwner()) return;
        if (struck.add(victim.getUUID()) && level() instanceof ServerLevel server) {
            float base = payload.getItem() instanceof DiscItem disc ? disc.damage : 5;
            float speedBonus = (float)Math.min(ricochets * 1.3, 6);
            living.hurtServer(server, damageSources().mobProjectile(this,
                getOwner() instanceof LivingEntity owner ? owner : null), base + speedBonus);
            if (returning) return;
            if (struck.size() >= 5) beginReturn();
        }
    }
}