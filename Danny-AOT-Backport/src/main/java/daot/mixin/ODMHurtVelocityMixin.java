package daot.mixin;

import daot.DannysAot;
import daot.ServerHookTracker;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class ODMHurtVelocityMixin {
   private Vec3d dannysaot$savedVelocity = null;

   @Inject(method = "damage", at = @At("HEAD"))
   private void saveVelocityBeforeHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
      LivingEntity entity = (LivingEntity)(Object)this;
      if (entity instanceof PlayerEntity player
         && DannysAot.isODMGear(player.getEquippedStack(EquipmentSlot.LEGS).getItem())
         && ServerHookTracker.hasActiveHook(player.getUuid())) {
         this.dannysaot$savedVelocity = player.getVelocity();
      }
   }

   @Inject(method = "damage", at = @At("RETURN"))
   private void restoreVelocityAfterHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
      LivingEntity entity = (LivingEntity)(Object)this;
      if (entity instanceof PlayerEntity player && this.dannysaot$savedVelocity != null) {
         player.setVelocity(this.dannysaot$savedVelocity);
         player.velocityModified = true;
         this.dannysaot$savedVelocity = null;
      }
   }
}

