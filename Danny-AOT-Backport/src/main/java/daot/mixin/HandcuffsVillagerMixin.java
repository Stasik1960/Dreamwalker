package daot.mixin;

import daot.HandcuffsItem;
import daot.HandcuffsKeyItem;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntity.class)
public class HandcuffsVillagerMixin {
   @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
   private void blockTradeWhenHoldingCuffs(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (player.getStackInHand(hand).getItem() instanceof HandcuffsItem || player.getStackInHand(hand).getItem() instanceof HandcuffsKeyItem) {
         cir.setReturnValue(ActionResult.SUCCESS);
      }
   }
}
