package daot.mixin;

import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.AttackTitanGrabEntity;
import daot.BeastTitanEntity;
import daot.BeastTitanGrabEntity;
import daot.ColossalTitanEntity;
import daot.ConnieFatherEntity;
import daot.CrawlerTitanEntity;
import daot.FemaleCrystalShellEntity;
import daot.FemaleTitanEntity;
import daot.FemaleTitanGrabEntity;
import daot.FritzTitanEntity;
import daot.GeassManager;
import daot.OgreTitanEntity;
import daot.SadTitanEntity;
import daot.SmallTitan2Entity;
import daot.SmallTitanEntity;
import daot.TitanEntity;
import daot.WarhammerTitanEntity;
import daot.YellowTitanEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityDismountMixin {
   @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
   private void preventTitanDismount(CallbackInfo ci) {
      Entity self = (Entity)(Object)this;
      if (!self.getWorld().isClient()) {
         if (self instanceof ServerPlayerEntity sp && GeassManager.isMindController(sp.getUuid())) {
            ci.cancel();
         } else {
            Entity vehicle = self.getVehicle();
            if (vehicle instanceof ColossalTitanEntity titan) {
               if (!titan.isDismountAllowed()) {
                  ci.cancel();
               }
            } else if (vehicle instanceof AttackTitanEntity attackTitan) {
               if (!attackTitan.isDismountAllowed()) {
                  ci.cancel();
               }
            } else if (vehicle instanceof ArmoredTitanEntity armoredTitan) {
               if (!armoredTitan.isDismountAllowed()) {
                  ci.cancel();
               }
            } else if (vehicle instanceof FemaleTitanEntity femaleTitan) {
               if (!femaleTitan.isDismountAllowed()) {
                  ci.cancel();
               }
            } else if (vehicle instanceof BeastTitanEntity beastTitan) {
               if (!beastTitan.isDismountAllowed()) {
                  ci.cancel();
               }
            } else if (vehicle instanceof WarhammerTitanEntity warhammerTitan) {
               if (!warhammerTitan.isDismountAllowed()) {
                  ci.cancel();
               }
            } else if (vehicle instanceof BeastTitanGrabEntity grabEntity) {
               if (!grabEntity.isDismountAllowed()) {
                  ci.cancel();
               }
            } else if (vehicle instanceof AttackTitanGrabEntity grabEntityx) {
               if (!grabEntityx.isDismountAllowed()) {
                  ci.cancel();
               }
            } else if (vehicle instanceof FemaleTitanGrabEntity grabEntityxx) {
               if (!grabEntityxx.isDismountAllowed()) {
                  ci.cancel();
               }
            } else if (vehicle instanceof FemaleCrystalShellEntity crystal) {
               if (!crystal.isShattering()) {
                  ci.cancel();
               }
            } else if (vehicle instanceof SmallTitanEntity smallTitan) {
               boolean titanDead = smallTitan.isDead();
               boolean passengerDead = self instanceof LivingEntity selfLiving && selfLiving.isDead();
               boolean isSelfInjection = smallTitan.isSelfInjectionPassenger(self);
               boolean isEating = smallTitan.isEating();
               boolean dismountAllowed = smallTitan.isDismountAllowed();
               if (dismountAllowed) {
                  return;
               }

               boolean shouldBlock;
               if (isSelfInjection) {
                  shouldBlock = true;
               } else {
                  shouldBlock = isEating;
               }

               if (!titanDead && !passengerDead && shouldBlock) {
                  if (isSelfInjection) {
                     smallTitan.notifyDismountBlocked(self);
                  }

                  ci.cancel();
               }
            } else if (vehicle instanceof SmallTitan2Entity smallTitan2) {
               boolean titanDeadx = smallTitan2.isDead();
               boolean passengerDeadx = self instanceof LivingEntity selfLivingx && selfLivingx.isDead();
               boolean isSelfInjectionx = smallTitan2.isSelfInjectionPassenger(self);
               boolean titanIsEating = smallTitan2.isEating();
               if (smallTitan2.isDismountAllowed()) {
                  return;
               }

               boolean shouldBlockx;
               if (isSelfInjectionx) {
                  shouldBlockx = true;
               } else {
                  shouldBlockx = titanIsEating;
               }

               if (!titanDeadx && !passengerDeadx && shouldBlockx) {
                  if (isSelfInjectionx) {
                     smallTitan2.notifyDismountBlocked(self);
                  }

                  ci.cancel();
               }
            } else if (vehicle instanceof YellowTitanEntity yellowTitan) {
               boolean titanDeadxx = yellowTitan.isDead();
               boolean passengerDeadxx = self instanceof LivingEntity selfLivingxx && selfLivingxx.isDead();
               boolean isSelfInjectionxx = yellowTitan.isSelfInjectionPassenger(self);
               if (yellowTitan.isDismountAllowed()) {
                  return;
               }

               boolean shouldBlockxx;
               if (isSelfInjectionxx) {
                  shouldBlockxx = true;
               } else {
                  shouldBlockxx = yellowTitan.isEating();
               }

               if (!titanDeadxx && !passengerDeadxx && shouldBlockxx) {
                  if (isSelfInjectionxx) {
                     yellowTitan.notifyDismountBlocked(self);
                  }

                  ci.cancel();
               }
            } else if (vehicle instanceof FritzTitanEntity fritzTitan) {
               boolean titanDeadxxx = fritzTitan.isDead();
               boolean passengerDeadxxx = self instanceof LivingEntity selfLivingxxx && selfLivingxxx.isDead();
               boolean isSelfInjectionxxx = fritzTitan.isSelfInjectionPassenger(self);
               if (fritzTitan.isDismountAllowed()) {
                  return;
               }

               boolean shouldBlockxxx;
               if (isSelfInjectionxxx) {
                  shouldBlockxxx = true;
               } else {
                  shouldBlockxxx = fritzTitan.isEating();
               }

               if (!titanDeadxxx && !passengerDeadxxx && shouldBlockxxx) {
                  if (isSelfInjectionxxx) {
                     fritzTitan.notifyDismountBlocked(self);
                  }

                  ci.cancel();
               }
            } else if (vehicle instanceof ConnieFatherEntity connieFather) {
               boolean titanDeadxxxx = connieFather.isDead();
               boolean passengerDeadxxxx = self instanceof LivingEntity selfLivingxxxx && selfLivingxxxx.isDead();
               boolean isSelfInjectionxxxx = connieFather.isSelfInjectionPassenger(self);
               if (connieFather.isDismountAllowed()) {
                  return;
               }

               boolean shouldBlockxxxx;
               if (isSelfInjectionxxxx) {
                  shouldBlockxxxx = true;
               } else {
                  shouldBlockxxxx = connieFather.isEating();
               }

               if (!titanDeadxxxx && !passengerDeadxxxx && shouldBlockxxxx) {
                  if (isSelfInjectionxxxx) {
                     connieFather.notifyDismountBlocked(self);
                  }

                  ci.cancel();
               }
            } else if (vehicle instanceof CrawlerTitanEntity crawlerTitan) {
               if (!crawlerTitan.isRideable) {
                  boolean titanDeadxxxxx = crawlerTitan.isDead();
                  boolean passengerDeadxxxxx = self instanceof LivingEntity selfLivingxxxxx && selfLivingxxxxx.isDead();
                  boolean isSelfInjectionxxxxx = crawlerTitan.isSelfInjectionPassenger(self);
                  if (!crawlerTitan.isDismountAllowed()) {
                     if (isSelfInjectionxxxxx && !titanDeadxxxxx && !passengerDeadxxxxx) {
                        crawlerTitan.notifyDismountBlocked(self);
                        ci.cancel();
                     } else if (crawlerTitan.isEating() && !titanDeadxxxxx && !passengerDeadxxxxx) {
                        ci.cancel();
                     }
                  }
               }
            } else if (vehicle instanceof OgreTitanEntity ogreTitan) {
               boolean titanDeadxxxxx = ogreTitan.isDead();
               boolean passengerDeadxxxxx = self instanceof LivingEntity selfLivingxxxxx && selfLivingxxxxx.isDead();
               boolean isSelfInjectionxxxxx = ogreTitan.isSelfInjectionPassenger(self);
               if (ogreTitan.isDismountAllowed()) {
                  return;
               }

               boolean shouldBlockxxxxx;
               if (isSelfInjectionxxxxx) {
                  shouldBlockxxxxx = true;
               } else {
                  shouldBlockxxxxx = ogreTitan.isEating();
               }

               if (!titanDeadxxxxx && !passengerDeadxxxxx && shouldBlockxxxxx) {
                  if (isSelfInjectionxxxxx) {
                     ogreTitan.notifyDismountBlocked(self);
                  }

                  ci.cancel();
               }
            } else if (vehicle instanceof SadTitanEntity sadTitan) {
               boolean titanDeadxxxxxx = sadTitan.isDead();
               boolean passengerDeadxxxxxx = self instanceof LivingEntity selfLivingxxxxxx && selfLivingxxxxxx.isDead();
               boolean isSelfInjectionxxxxxx = sadTitan.isSelfInjectionPassenger(self);
               if (sadTitan.isDismountAllowed()) {
                  return;
               }

               boolean shouldBlockxxxxxx;
               if (isSelfInjectionxxxxxx) {
                  shouldBlockxxxxxx = true;
               } else {
                  shouldBlockxxxxxx = sadTitan.isEating();
               }

               if (!titanDeadxxxxxx && !passengerDeadxxxxxx && shouldBlockxxxxxx) {
                  if (isSelfInjectionxxxxxx) {
                     sadTitan.notifyDismountBlocked(self);
                  }

                  ci.cancel();
               }
            } else if (vehicle instanceof TitanEntity titanx) {
               boolean titanDeadxxxxxxx = titanx.isDead();
               boolean passengerDeadxxxxxxx = self instanceof LivingEntity selfLivingxxxxxxx && selfLivingxxxxxxx.isDead();
               boolean isSelfInjectionxxxxxxx = titanx.isSelfInjectionPassenger(self);
               if (titanx.isDismountAllowed()) {
                  return;
               }

               boolean shouldBlockxxxxxxx;
               if (isSelfInjectionxxxxxxx) {
                  shouldBlockxxxxxxx = true;
               } else {
                  shouldBlockxxxxxxx = titanx.isEating();
               }

               if (!titanDeadxxxxxxx && !passengerDeadxxxxxxx && shouldBlockxxxxxxx) {
                  if (isSelfInjectionxxxxxxx) {
                     titanx.notifyDismountBlocked(self);
                  }

                  ci.cancel();
               }
            }
         }
      }
   }
}

