package daot.mixin;

import daot.BloodlineData;
import daot.BloodlineType;
import daot.ModEffects;
import daot.PowerDamageMarker;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class PowerOutgoingDamageMixin {
   @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
   private float daot$amplifyPowerDamage(float amount, DamageSource source) {
      if (PowerDamageMarker.isApplyingAbility()) {
         return amount;
      } else if (source.getAttacker() instanceof PlayerEntity attacker) {
         if (attacker.getWorld() instanceof ServerWorld sl) {
            BloodlineType bl = BloodlineData.get(sl).getBloodline(attacker.getUuid());
            if (bl != BloodlineType.HOMELANDER
               && bl != BloodlineType.SOLDIERBOY
               && bl != BloodlineType.ATRAIN
               && bl != BloodlineType.TRANSLUCENT
               && bl != BloodlineType.BUTCHER) {
               return amount;
            } else {
               return ModEffects.isPowerDisabled(attacker) ? amount : amount * 3.0F;
            }
         } else {
            return amount;
         }
      } else {
         return amount;
      }
   }
}
