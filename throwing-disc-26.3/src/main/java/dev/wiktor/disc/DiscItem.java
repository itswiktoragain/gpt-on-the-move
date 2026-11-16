package dev.wiktor.disc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DiscItem extends Item {
    final int damage;
    final double distance;
    public DiscItem(int damage, double distance, Properties properties) {
        super(properties); this.damage = damage; this.distance = distance;
    }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        // A short 10-tick (0.5-second) recovery between throws, with a visible
        // vanilla cooldown overlay. Check on both sides for responsive handling.
        if (player.getCooldowns().isOnCooldown(held)) {
            return InteractionResult.FAIL;
        }
        player.getCooldowns().addCooldown(held, 10);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.75f, 1.3f);
        if (level instanceof ServerLevel server) {
            boolean creative = player.getAbilities().instabuild;
            ItemStack flying = held.copyWithCount(1);
            if (!creative) held.shrink(1);
            DiscEntity projectile = new DiscEntity(level, player, flying, this, creative, hand);
            projectile.setPos(player.getX(), player.getEyeY() - 0.15, player.getZ());
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.8f, 0.35f);
            // Remember the facing direction at release, not the projectile's
            // changing velocity. This is synced to other clients by DiscEntity.
            projectile.setThrowYaw(player.getYRot());
            server.addFreshEntity(projectile);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }
}