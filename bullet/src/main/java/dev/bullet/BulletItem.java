package dev.bullet;

import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class BulletItem extends Item {
    public BulletItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos placeAt = context.getBlockPos().offset(context.getSide());

        if (!world.isClient) {
            BulletEntity bullet = new BulletEntity(BulletMod.BULLET_ENTITY, world);
            bullet.refreshPositionAndAngles(
                placeAt.getX() + 0.5, placeAt.getY() + 0.03, placeAt.getZ() + 0.5,
                context.getPlayer() == null ? 0 : context.getPlayer().getYaw(), 0
            );
            world.spawnEntity(bullet);
            if (context.getPlayer() == null || !context.getPlayer().isCreative()) {
                context.getStack().decrement(1);
            }
        }
        return ActionResult.SUCCESS;
    }
}
