package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ODMConfig {
   private static ODMConfig INSTANCE = new ODMConfig();
   public long dualHookEaseTime = 2000L;
   public int gasTickInterval = 10;
   public int gasConsumptionNormal = 1;
   public int gasConsumptionBoost = 3;
   public double basePullSpeed = 0.12;
   public double dualHookPullMultiplier = 2.4;
   public double dualHookBoostPullMultiplier = 1.5;
   public double orbitPullMultiplier = 1.8;
   public double baseOrbitSpeed = 0.08;
   public double dualHookOrbitMultiplier = 0.4;
   public double upwardLift = 0.05;
   public double boostPullMultiplier = 1.0;
   public double boostOrbitMultiplier = 2.5;
   public double boostRampRate = 0.0588;
   public double boostDecayRate = 0.0125;
   public double maxHookDistance = 250.0;
   public long momentumPreserveTime = 4500L;
   public double flightSoundVelocityThreshold = 0.3;

   public static ODMConfig get() {
      return INSTANCE;
   }

   public static void applySyncedConfig(
      long dualHookEaseTime,
      int gasTickInterval,
      int gasConsumptionNormal,
      int gasConsumptionBoost,
      double basePullSpeed,
      double dualHookPullMultiplier,
      double dualHookBoostPullMultiplier,
      double orbitPullMultiplier,
      double baseOrbitSpeed,
      double dualHookOrbitMultiplier,
      double upwardLift,
      double boostPullMultiplier,
      double boostOrbitMultiplier,
      double boostRampRate,
      double boostDecayRate,
      double maxHookDistance,
      long momentumPreserveTime,
      double flightSoundVelocityThreshold
   ) {
      INSTANCE.dualHookEaseTime = dualHookEaseTime;
      INSTANCE.gasTickInterval = gasTickInterval;
      INSTANCE.gasConsumptionNormal = gasConsumptionNormal;
      INSTANCE.gasConsumptionBoost = gasConsumptionBoost;
      INSTANCE.basePullSpeed = basePullSpeed;
      INSTANCE.dualHookPullMultiplier = dualHookPullMultiplier;
      INSTANCE.dualHookBoostPullMultiplier = dualHookBoostPullMultiplier;
      INSTANCE.orbitPullMultiplier = orbitPullMultiplier;
      INSTANCE.baseOrbitSpeed = baseOrbitSpeed;
      INSTANCE.dualHookOrbitMultiplier = dualHookOrbitMultiplier;
      INSTANCE.upwardLift = upwardLift;
      INSTANCE.boostPullMultiplier = boostPullMultiplier;
      INSTANCE.boostOrbitMultiplier = boostOrbitMultiplier;
      INSTANCE.boostRampRate = boostRampRate;
      INSTANCE.boostDecayRate = boostDecayRate;
      INSTANCE.maxHookDistance = maxHookDistance;
      INSTANCE.momentumPreserveTime = momentumPreserveTime;
      INSTANCE.flightSoundVelocityThreshold = flightSoundVelocityThreshold;
      DannysAot.LOGGER.info("Applied synced config from server");
   }

   public static void resetToDefaults() {
      INSTANCE = new ODMConfig();
   }
}
