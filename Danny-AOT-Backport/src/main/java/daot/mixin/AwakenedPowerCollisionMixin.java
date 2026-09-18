package daot.mixin;

import daot.AwakenedPowerTracker;
import daot.FritzTitanEntity;
import daot.GeassManager;
import daot.SmallTitan2Entity;
import daot.SmallTitanEntity;
import daot.TitanEntity;
import daot.VanishManager;
import daot.YellowTitanEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class AwakenedPowerCollisionMixin {
   @Inject(method = "collidesWith", at = @At("HEAD"), cancellable = true)
   private void daot$skipTitanCollision(Entity other, CallbackInfoReturnable<Boolean> cir) {
      Entity self = (Entity)(Object)this;
      if (self instanceof ServerPlayerEntity sp && GeassManager.isMindController(sp.getUuid())) {
         cir.setReturnValue(false);
      } else if (other instanceof ServerPlayerEntity sp && GeassManager.isMindController(sp.getUuid())) {
         cir.setReturnValue(false);
      } else if (self instanceof ServerPlayerEntity sp && VanishManager.isVanished(sp.getUuid())) {
         cir.setReturnValue(false);
      } else if (other instanceof ServerPlayerEntity sp && VanishManager.isVanished(sp.getUuid())) {
         cir.setReturnValue(false);
      } else {
         if (other instanceof TitanEntity
            || other instanceof SmallTitanEntity
            || other instanceof SmallTitan2Entity
            || other instanceof FritzTitanEntity
            || other instanceof YellowTitanEntity) {
            boolean hasAbility;
            if (self.getWorld().isClient()) {
               hasAbility = AwakenedPowerTracker.isClientPlayerActive(self.getUuid());
            } else {
               hasAbility = self.getCommandTags().contains("dannys-aot:awakened_power");
            }

            if (hasAbility) {
               cir.setReturnValue(false);
               return;
            }

            if (other instanceof TitanEntity titan && titan.isEating() && titan.getEatingTarget() == self) {
               cir.setReturnValue(false);
               return;
            }

            if (other instanceof FritzTitanEntity fritz && fritz.isEating() && fritz.getEatingTarget() == self) {
               cir.setReturnValue(false);
               return;
            }

            if (other instanceof SmallTitanEntity small && small.isEating() && small.getEatingTarget() == self) {
               cir.setReturnValue(false);
               return;
            }

            if (other instanceof SmallTitan2Entity small2 && small2.isEating() && small2.getEatingTarget() == self) {
               cir.setReturnValue(false);
               return;
            }
         }
      }
   }
}

