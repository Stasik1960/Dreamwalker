package daot.mixin;

import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.ColossalTitanEntity;
import daot.FemaleTitanEntity;
import daot.HandcuffsTracker;
import daot.WarhammerTitanEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class TitanItemPickupMixin {
   @Shadow
   public abstract ItemStack getStack();

   @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
   private void blockPickupWhileShifted(PlayerEntity player, CallbackInfo ci) {
      Entity vehicle = player.getVehicle();
      if (vehicle instanceof ColossalTitanEntity titan && !titan.isDismounting()) {
         ci.cancel();
      } else if (vehicle instanceof AttackTitanEntity titan && !titan.isDismounting()) {
         ci.cancel();
      } else if (vehicle instanceof ArmoredTitanEntity titan && !titan.isDismounting()) {
         ci.cancel();
      } else if (vehicle instanceof FemaleTitanEntity titan && !titan.isDismounting()) {
         ci.cancel();
      } else if (vehicle instanceof WarhammerTitanEntity titan && !titan.isDismounting()) {
         ci.cancel();
      }

      if (HandcuffsTracker.isCuffed(player.getUuid())) {
         ci.cancel();
      }
   }
}
