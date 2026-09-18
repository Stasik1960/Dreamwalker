package daot.mixin;

import daot.DannysAot;
import daot.ServerHookTracker;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class ServerFallDamageMixin {
   @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
   private void preventFallDamageWithODM(float fallDistance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
      LivingEntity entity = (LivingEntity)(Object)this;
      if (entity instanceof PlayerEntity player
         && DannysAot.isODMGear(player.getEquippedStack(EquipmentSlot.LEGS).getItem())
         && ServerHookTracker.hasActiveHook(player.getUuid())) {
         cir.setReturnValue(false);
      }
   }

   @ModifyVariable(method = "handleFallDamage", at = @At("HEAD"), ordinal = 0, argsOnly = true)
   private float reduceFallDamageWithODMBoots(float fallDistance) {
      LivingEntity entity = (LivingEntity)(Object)this;
      return entity instanceof PlayerEntity player && player.getEquippedStack(EquipmentSlot.FEET).getItem() == DannysAot.ODM_BOOTS
         ? fallDistance * 0.3F
         : fallDistance;
   }
}

