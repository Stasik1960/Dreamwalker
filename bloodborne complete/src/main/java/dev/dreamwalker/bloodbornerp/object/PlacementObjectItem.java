package dev.dreamwalker.bloodbornerp.object;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/** Builder-only placement item; the server revalidates all placement geometry. */
final class PlacementObjectItem extends Item {
 private final String objectId;
 PlacementObjectItem(String objectId) { super(new Settings().maxCount(16)); this.objectId=objectId; }
 @Override public ActionResult useOnBlock(net.minecraft.item.ItemUsageContext context) {
  PlayerEntity player=context.getPlayer(); if(player==null || (!player.isCreative() && !player.hasPermissionLevel(2))) return ActionResult.FAIL;
  if(context.getWorld().isClient) return ActionResult.SUCCESS;
  var placement=new net.minecraft.item.ItemPlacementContext(context);
  if(!placement.canPlace()||!ObjectRegistry.place(player,objectId,placement.getBlockPos(),context.getPlayerYaw(),context.getStack()))return ActionResult.FAIL;
  if(!player.isCreative())context.getStack().decrement(1);
  return ActionResult.CONSUME;
 }
}
