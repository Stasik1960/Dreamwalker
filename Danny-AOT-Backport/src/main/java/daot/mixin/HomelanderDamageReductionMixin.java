package daot.mixin;

import daot.BloodlineData;
import daot.BloodlineType;
import daot.ModEffects;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayerEntity.class)
public abstract class HomelanderDamageReductionMixin {
   @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
   private float daot$reducePowerDamage(float amount, DamageSource source) {
      ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
      if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         return amount;
      } else {
         BloodlineType bl = BloodlineData.get(self.getServerWorld()).getBloodline(self.getUuid());
         if (bl != BloodlineType.HOMELANDER
            && bl != BloodlineType.SOLDIERBOY
            && bl != BloodlineType.ATRAIN
            && bl != BloodlineType.TRANSLUCENT
            && bl != BloodlineType.BUTCHER) {
            if (self.getCommandTags().contains("ogre_shifter")) {
               return amount * 0.2F;
            } else {
               return self.getCommandTags().contains("titan_bloodline") ? amount * 0.55F : amount;
            }
         } else if (ModEffects.isPowerDisabled(self)) {
            return amount;
         } else if (source.isIn(DamageTypeTags.IS_FIRE)) {
            return 0.0F;
         } else {
            if (bl == BloodlineType.HOMELANDER || bl == BloodlineType.SOLDIERBOY) {
               if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
                  return 0.0F;
               }

               if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
                  return 0.0F;
               }
            }

            if ((bl == BloodlineType.TRANSLUCENT || bl == BloodlineType.BUTCHER) && source.isIn(DamageTypeTags.IS_PROJECTILE)) {
               return 0.0F;
            } else if (bl == BloodlineType.HOMELANDER) {
               return amount * 0.1F;
            } else {
               return bl != BloodlineType.TRANSLUCENT && bl != BloodlineType.BUTCHER ? amount * 0.14285715F : amount * 0.2857143F;
            }
         }
      }
   }
}

