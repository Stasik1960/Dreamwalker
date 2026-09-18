package daot;

import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

public class HandcuffsKeyItem extends Item {
   public HandcuffsKeyItem(Settings properties) {
      super(properties);
   }

   @Override
   public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
      tooltip.add(Text.literal("Releasing Players/Villagers from handcuffs").formatted(Formatting.DARK_GRAY));
      super.appendTooltip(stack, context, tooltip, type);
   }

   @Override
   public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
      if (user.getWorld().isClient()) {
         return HandcuffsTracker.isCuffed(entity) ? ActionResult.SUCCESS : ActionResult.PASS;
      } else if ((entity instanceof PlayerEntity || entity instanceof VillagerEntity) && HandcuffsTracker.isCuffed(entity)) {
         HandcuffsTracker.uncuffEntity(entity, user);
         if (user instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(Text.literal("Released the cuffs!").formatted(Formatting.GREEN), true);
         }

         return ActionResult.SUCCESS;
      } else {
         return ActionResult.PASS;
      }
   }
}
