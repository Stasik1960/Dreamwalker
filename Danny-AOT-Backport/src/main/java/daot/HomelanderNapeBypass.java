package daot;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class HomelanderNapeBypass {
   private HomelanderNapeBypass() {
   }

   public static boolean isHomelanderAttacker(LivingEntity attacker, World level) {
      if (attacker instanceof PlayerEntity player) {
         return level instanceof ServerWorld serverLevel ? BloodlineData.get(serverLevel).getBloodline(player.getUuid()) == BloodlineType.HOMELANDER : false;
      } else {
         return false;
      }
   }
}
