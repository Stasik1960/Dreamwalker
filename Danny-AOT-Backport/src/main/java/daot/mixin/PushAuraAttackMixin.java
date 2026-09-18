package daot.mixin;

import daot.PushAuraTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PushAuraAttackMixin {
   private static final Identifier FREEZE_DAMAGE_ID = new Identifier("dannys-aot", "push_aura_freeze_damage");

   @Inject(method = "attack(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
   private void dannysaot$pushAuraCancel(Entity target, CallbackInfo ci) {
      PlayerEntity attacker = (PlayerEntity)(Object)this;
      EntityAttributeInstance dmgAttr = attacker.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
      if (dmgAttr != null) {
         EntityAttributeModifier mod = daot.compat.AttributeModifiers.getModifier(dmgAttr, FREEZE_DAMAGE_ID);
         if (mod != null && mod.getValue() < -0.8) {
            ci.cancel();
            return;
         }
      }

      if (attacker.getWorld().isClient && PushAuraTracker.clientLocalFrozenVignette) {
         ci.cancel();
      } else {
         if (target instanceof PlayerEntity targetPlayer && PushAuraTracker.isActive(targetPlayer.getUuid())) {
            double distance = attacker.distanceTo(targetPlayer);
            if (distance > 0.7) {
               ci.cancel();
            }
         }
      }
   }
}

