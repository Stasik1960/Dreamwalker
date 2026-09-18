package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class TitanDashClientData {
   public static final int PHASE_IDLE = 0;
   public static final int PHASE_CHARGE = 1;
   public static final int PHASE_DASH = 2;
   private static final int CHARGE_TICKS = 25;
   private static final int DASH_TICKS = 10;
   public static final double DASH_SPEED = 5.0;
   private static volatile float charge = 0.0F;
   private static volatile float maxCharge = 300.0F;
   private static volatile int phase = 0;
   private static volatile Vec3d dir = Vec3d.ZERO;
   private static int dashTicksLeft = 0;
   private static int chargeTicks = 0;

   public static void update(float newCharge, float newMax, int newPhase, float dx, float dy, float dz) {
      charge = newCharge;
      maxCharge = newMax;
      dir = new Vec3d(dx, dy, dz);
      if (newPhase != phase) {
         if (newPhase == 2) {
            dashTicksLeft = 10;
         }

         if (newPhase == 1) {
            chargeTicks = 0;
         }
      }

      phase = newPhase;
   }

   public static void clientTick() {
      if (phase == 1 && chargeTicks < 25) {
         chargeTicks++;
      }

      if (phase == 2 && dashTicksLeft > 0) {
         dashTicksLeft--;
      }
   }

   public static boolean isCharging() {
      return phase == 1;
   }

   public static boolean isDashing() {
      return phase == 2 && dashTicksLeft > 0;
   }

   public static float chargeProgress() {
      return Math.min(1.0F, chargeTicks / 25.0F);
   }

   public static Vec3d getDir() {
      return dir;
   }

   public static float getCharge() {
      return charge;
   }

   public static float getMaxCharge() {
      return maxCharge;
   }

   public static void reset() {
      charge = 0.0F;
      phase = 0;
      dir = Vec3d.ZERO;
      dashTicksLeft = 0;
      chargeTicks = 0;
   }
}
