package daot.compat.attributes;

import net.minecraft.entity.LivingEntity;

public final class LivingEntityAttributeCompat {
   private LivingEntityAttributeCompat() {
   }

   public static float getScale(LivingEntity entity) {
      if (entity.getAttributes() == null || !entity.getAttributes().hasAttribute(DaotEntityAttributes.SCALE)) {
         return 1.0F;
      }

      return (float)entity.getAttributeValue(DaotEntityAttributes.SCALE);
   }

   public static double getExplosionKnockbackFactor(LivingEntity entity) {
      if (!entity.getAttributes().hasAttribute(DaotEntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE)) {
         return 1.0;
      }

      return 1.0 - entity.getAttributeValue(DaotEntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE);
   }
}
