package daot.mixin;

import daot.DannysAot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity.PositionUpdater;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class HandcuffsPassengerPositionMixin {
   @Inject(method = "updatePassengerPosition(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity$PositionUpdater;)V", at = @At("HEAD"), cancellable = true)
   private void dannysaot$repositionCuffedPassenger(Entity passenger, PositionUpdater moveFunc, CallbackInfo ci) {
      Entity self = (Entity)(Object)this;
      if (self instanceof PlayerEntity player) {
         if (passenger instanceof PlayerEntity || passenger instanceof VillagerEntity) {
            float yawRad = (float)Math.toRadians(player.getYaw());
            double sin = Math.sin(yawRad);
            double cos = Math.cos(yawRad);
            ItemStack legs = player.getEquippedStack(EquipmentSlot.LEGS);
            boolean hasODM = !legs.isEmpty() && DannysAot.isODMGear(legs.getItem());
            double posX;
            double posZ;
            if (hasODM) {
               posX = player.getX() + sin;
               posZ = player.getZ() - cos;
            } else {
               posX = player.getX() - sin;
               posZ = player.getZ() + cos;
            }

            moveFunc.accept(passenger, posX, player.getY(), posZ);
            ci.cancel();
         }
      }
   }
}

