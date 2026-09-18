package daot;

import net.minecraft.entity.LivingEntity;

public final class TitanGrabImmunity {
   private TitanGrabImmunity() {
   }

   public static boolean isImmune(LivingEntity target) {
      if (target == null) {
         return false;
      } else {
         return target.getCommandTags().contains("dannys-aot:awakened_power") ? true : HomelanderFlightServerHandler.isFlying(target.getUuid());
      }
   }
}
