package daot.mixin;

import daot.DannysAot;
import daot.OgreShifterTitanEntity;
import daot.ShifterTitan;
import daot.TestShifterTitanEntity;
import daot.TripleTTitanEntity;
import daot.network.ModNetworking;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ShifterForceShiftMixin {
   @Inject(method = "tryUseTotem", at = @At("HEAD"), cancellable = true)
   private void forceShiftOnLethalDamage(DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
      LivingEntity self = (LivingEntity)(Object)this;
      if (self instanceof ServerPlayerEntity player) {
         if (!damageSource.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (player.getWorld().getGameRules().getBoolean(DannysAot.RULE_FORCE_SHIFTING)) {
               Entity vehicle = player.getVehicle();
               if (!(vehicle instanceof ShifterTitan)
                  && !(vehicle instanceof OgreShifterTitanEntity)
                  && !(vehicle instanceof TestShifterTitanEntity)
                  && !(vehicle instanceof TripleTTitanEntity)) {
                  Set<String> tags = player.getCommandTags();
                  boolean hasShifter = tags.contains("attack")
                     || tags.contains("armored")
                     || tags.contains("beast")
                     || tags.contains("colossal")
                     || tags.contains("female")
                     || tags.contains("warhammer")
                     || tags.contains("founder")
                     || tags.contains("triple_t")
                     || tags.contains("ogre_shifter")
                     || tags.contains("jaw");
                  if (hasShifter) {
                     if (!player.getCommandTags().contains("titan_stealth")) {
                        float playerMax = ModNetworking.getMaxStaminaForPlayer(player);
                        float stamina = ModNetworking.getCurrentStamina(player.getUuid(), playerMax);
                        if (!(stamina < playerMax * 0.9F)) {
                           player.setHealth(1.0F);
                           player.clearStatusEffects();
                           player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1, false, false));
                           player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 3, false, false));
                           player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0, false, false));
                           ModNetworking.forceShiftPlayer(player);
                           cir.setReturnValue(true);
                        }
                     }
                  }
               }
            }
         }
      }
   }
}

