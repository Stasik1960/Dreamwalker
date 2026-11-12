package dev.nightwatch.carriage;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class CarriageItem extends Item {
    CarriageItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;
        BlockPos pos = context.getBlockPos().offset(context.getSide());
        CarriageEntity carriage = NightWatchCarriage.CARRIAGE.create(world);
        if (carriage == null) return ActionResult.FAIL;
        carriage.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() + .5, context.getPlayerYaw(), 0f);
        if (!world.isSpaceEmpty(carriage, carriage.getBoundingBox()) || !world.spawnEntity(carriage)) {
            if (context.getPlayer() != null) {
                context.getPlayer().sendMessage(Text.translatable("hint.nightwatchcarriage.placement"), true);
            }
            return ActionResult.FAIL;
        }
        if (context.getPlayer() == null || !context.getPlayer().isCreative()) context.getStack().decrement(1);
        return ActionResult.CONSUME;
    }
}
