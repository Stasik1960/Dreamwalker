package daot.mixin;

import daot.PushAuraTracker;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PushAuraPotionMixin {
   @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
   private void dannysaot$blockPotionIfNotInHitbox(StatusEffectInstance effectInstance, Entity source, CallbackInfoReturnable<Boolean> cir) {
      if (source != null) {
         LivingEntity var5 = (LivingEntity)(Object)this;
         if (var5 instanceof ServerPlayerEntity player) {
            if (PushAuraTracker.isActive(player.getUuid())) {
               if ((source instanceof PotionEntity || source instanceof AreaEffectCloudEntity)
                  && !player.getBoundingBox().expand(0.5).contains(source.getPos())) {
                  cir.setReturnValue(false);
               }
            }
         }
      }
   }
}

