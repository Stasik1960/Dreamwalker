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
 @Override public net.minecraft.text.Text getName(ItemStack stack){return dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.itemName(stack,super.getName(stack));}
 @Override public void appendTooltip(ItemStack stack,World world,java.util.List<net.minecraft.text.Text> tooltip,net.minecraft.client.item.TooltipContext context){super.appendTooltip(stack,world,tooltip,context);dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.itemTooltip(stack,tooltip);}
 @Override public ActionResult useOnBlock(net.minecraft.item.ItemUsageContext context) {
  PlayerEntity player=context.getPlayer(); if(player==null || (!player.isCreative() && !player.hasPermissionLevel(2))) return ObjectRegistry.refuseItem(context,objectId,"creative_or_operator_required");
  if(dev.dreamwalker.bloodbornedw.architecture.BuildingTool.isHeld(player))return ActionResult.PASS;
  if(context.getWorld().isClient) return ActionResult.SUCCESS;
  if(RpObjectGeometry.surfaceMounted(ObjectRegistry.canonicalId(objectId))){
   if(!ObjectRegistry.withItemContext(context,()->ObjectRegistry.placeOnSurface(player,objectId,context.getHitPos(),context.getSide(),context.getPlayerYaw(),context.getStack())))return ActionResult.FAIL;
   if(!player.isCreative())context.getStack().decrement(1);return ActionResult.CONSUME;
  }
  var placement=new net.minecraft.item.ItemPlacementContext(context);
  // RP is an entity: no native carrier is replaced, so canPlace() is not an occupancy gate.
  if(!ObjectRegistry.withItemContext(context,()->ObjectRegistry.place(player,objectId,placement.getBlockPos(),context.getPlayerYaw(),context.getStack())))return ActionResult.FAIL;
  if(!player.isCreative())context.getStack().decrement(1);
  return ActionResult.CONSUME;
 }
}
