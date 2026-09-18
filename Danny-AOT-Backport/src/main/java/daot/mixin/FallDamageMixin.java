package daot.mixin;

import daot.DannysAot;
import daot.HookPoint;
import daot.ODMTickHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public class FallDamageMixin {
   @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
   private void preventFallDamageWithODM(float fallDistance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
      LivingEntity entity = (LivingEntity)(Object)this;
      if (entity instanceof PlayerEntity player && DannysAot.isODMGear(player.getEquippedStack(EquipmentSlot.LEGS).getItem())) {
         HookPoint leftHook = ODMTickHandler.getLeftHook(player.getUuid());
         HookPoint rightHook = ODMTickHandler.getRightHook(player.getUuid());
         if (leftHook != null && leftHook.active || rightHook != null && rightHook.active) {
            cir.setReturnValue(false);
         }
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

