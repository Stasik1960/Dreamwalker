package daot.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class TitanBloodlineEffectMixin {
   @Inject(method = "canHaveStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;)Z", at = @At("HEAD"), cancellable = true)
   private void dannysaot$titanBloodlinePoisonWitherImmunity(StatusEffectInstance effectInstance, CallbackInfoReturnable<Boolean> cir) {
      LivingEntity var4 = (LivingEntity)(Object)this;
      if (var4 instanceof ServerPlayerEntity player) {
         if (player.getCommandTags().contains("titan_bloodline")) {
            if (effectInstance.getEffectType() == StatusEffects.POISON || effectInstance.getEffectType() == StatusEffects.WITHER) {
               cir.setReturnValue(false);
            }
         }
      }
   }
}

