package daot.mixin.compat.attributes;

import daot.compat.attributes.LivingEntityAttributeCompat;

import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Explosion.class)
public abstract class ExplosionAttributeMixin {
   @Redirect(
      method = "collectBlocksAndDamageEntities",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V")
   )
   private void daot$reduceExplosionKnockback(Entity entity, Vec3d newVelocity) {
      if (entity instanceof LivingEntity living) {
         Vec3d oldVelocity = entity.getVelocity();
         Vec3d impulse = newVelocity.subtract(oldVelocity)
            .multiply(LivingEntityAttributeCompat.getExplosionKnockbackFactor(living));
         entity.setVelocity(oldVelocity.add(impulse));
      } else {
         entity.setVelocity(newVelocity);
      }
   }

   @Redirect(
      method = "collectBlocksAndDamageEntities",
      at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
   )
   private Object daot$syncReducedPlayerKnockback(Map<PlayerEntity, Vec3d> map, Object key, Object value) {
      PlayerEntity player = (PlayerEntity)key;
      Vec3d impulse = (Vec3d)value;
      return map.put(player, impulse.multiply(LivingEntityAttributeCompat.getExplosionKnockbackFactor(player)));
   }
}
