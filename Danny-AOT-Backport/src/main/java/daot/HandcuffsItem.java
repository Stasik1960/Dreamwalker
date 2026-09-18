package daot;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

public class HandcuffsItem extends Item {
   public HandcuffsItem(Settings properties) {
      super(properties);
   }

   @Override
   public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
      tooltip.add(Text.literal("Hold right click for 5 sec to cuff Player/Villager").formatted(Formatting.DARK_GRAY));
      super.appendTooltip(stack, context, tooltip, type);
   }

   @Override
   public UseAction getUseAction(ItemStack stack) {
      return UseAction.BOW;
   }

   @Override
   public int getMaxUseTime(ItemStack stack) {
      return 72000;
   }

   @Override
   public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
      if (user instanceof ServerPlayerEntity player) {
         HandcuffsTracker.CuffingSession session = HandcuffsTracker.getSession(player.getUuid());
         if (session == null) {
            player.clearActiveItem();
         } else {
            Entity target = world.getEntityById(session.targetEntityId);
            if (target == null || !target.isAlive() || player.distanceTo(target) > 6.0) {
               HandcuffsTracker.cancelCuffingSession(player.getUuid());
               player.clearActiveItem();
            } else if (HandcuffsTracker.isCuffed(target)) {
               HandcuffsTracker.cancelCuffingSession(player.getUuid());
               player.clearActiveItem();
            } else {
               session.ticksHeld++;
               if (session.ticksHeld >= 50) {
                  HandcuffsTracker.cuffEntity(target, player);
                  if (!player.isCreative()) {
                     stack.decrement(1);
                  }

                  player.sendMessage(Text.literal("Target has been cuffed!").formatted(Formatting.GREEN), true);
                  HandcuffsTracker.cancelCuffingSession(player.getUuid());
                  player.clearActiveItem();
               } else {
                  float secondsRemaining = (50 - session.ticksHeld) / 20.0F;
                  String msg = String.format("Cuffing %s (%.1f)", session.targetName, secondsRemaining);
                  player.sendMessage(Text.literal(msg).formatted(Formatting.GOLD), true);
               }
            }
         }
      }
   }

   @Override
   public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
      if (user instanceof PlayerEntity player) {
         HandcuffsTracker.cancelCuffingSession(player.getUuid());
      }
   }
}
