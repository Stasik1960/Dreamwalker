package daot.mixin;

import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.BeastTitanEntity;
import daot.BladeItem;
import daot.ColossalTitanEntity;
import daot.DannysAot;
import daot.FemaleTitanEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public class BladeSwingMixin {
   @Inject(method = "swingHand(Lnet/minecraft/util/Hand;Z)V", at = @At("HEAD"), cancellable = true)
   private void dannysaot$replaceBladeSwing(Hand hand, boolean sendPacket, CallbackInfo ci) {
      if ((Object)this instanceof ClientPlayerEntity player) {
         if (!(player.getVehicle() instanceof AttackTitanEntity)
            && !(player.getVehicle() instanceof ArmoredTitanEntity)
            && !(player.getVehicle() instanceof FemaleTitanEntity)
            && !(player.getVehicle() instanceof BeastTitanEntity)
            && !(player.getVehicle() instanceof ColossalTitanEntity)) {
            ItemStack heldItem = player.getStackInHand(hand);
            boolean heldBlade = heldItem.getItem() == DannysAot.BLADE && BladeItem.getBladeState(heldItem) != BladeItem.BladeState.EMPTY;
            if (heldBlade) {
               ci.cancel();
            }
         } else {
            ci.cancel();
         }
      }
   }
}

