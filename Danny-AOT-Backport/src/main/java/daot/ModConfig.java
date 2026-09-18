package daot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public class ModConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("dannys-aot.json");
   private static ModConfig INSTANCE;
   public int schemaVersion = 2;
   public boolean strictConfig = false;
   public long dualHookEaseTime = 2000L;
   public int gasTickInterval = 10;
   public int gasConsumptionNormal = 1;
   public int gasConsumptionBoost = 3;
   public double basePullSpeed = 0.12;
   public double dualHookPullMultiplier = 2.4;
   public double dualHookBoostPullMultiplier = 1.5;
   public double orbitPullMultiplier = 1.8;
   public double baseOrbitSpeed = 0.08;
   public double dualHookOrbitMultiplier = 1.0;
   public double upwardLift = 0.05;
   public double boostPullMultiplier = 1.0;
   public double boostOrbitMultiplier = 2.5;
   public double boostRampRate = 0.0588;
   public double boostDecayRate = 0.0125;
   public double maxHookDistance = 250.0;
   public long momentumPreserveTime = 4500L;
   public double flightSoundVelocityThreshold = 0.3;
   public float attackMaxStamina = 2000.0F;
   public float armoredMaxStamina = 4000.0F;
   public float colossalMaxStamina = 2000.0F;
   public float femaleMaxStamina = 2000.0F;
   public float beastMaxStamina = 6000.0F;
   public double attackTitanHealth = 400.0;
   public double armoredTitanHealth = 400.0;
   public double colossalTitanHealth = 1000.0;
   public double femaleTitanHealth = 267.0;
   public double beastTitanHealth = 400.0;
   public int attackTitanNapeHits = 3;
   public int armoredTitanNapeHits = 3;
   public int colossalTitanNapeHits = 3;
   public int femaleTitanNapeHits = 3;
   public int beastTitanNapeHits = 3;
   public int warhammerTitanNapeHits = 3;
   public boolean grandfatherLegacyShifterTags = false;
   public int unbackedTagGraceTicks = 100;
   public double pureTitanWanderSpeed = 0.09;
   public double pureTitanChaseSpeed = 0.27;
   public double smallTitanWanderSpeed = 0.12;
   public double smallTitanChaseSpeed = 0.27;
   public double smallTitan2WanderSpeed = 0.15;
   public double smallTitan2ChaseSpeed = 0.34;
   public double yellowTitanWanderSpeed = 0.15;
   public double yellowTitanChaseSpeed = 0.32;
   public double fritzTitanWanderSpeed = 0.09;
   public double fritzTitanChaseSpeed = 0.27;
   public double titanBeardWanderSpeed = 0.09;
   public double titanBeardChaseSpeed = 0.27;
   public double titanTropicalWanderSpeed = 0.09;
   public double titanTropicalChaseSpeed = 0.27;
   public double pureTitanEatingDamage = 3.0;
   public boolean canStunWhileGrabbed = true;
   public double attackTitanAttackDamage = 15.0;
   public double attackTitanKickDamage = 20.0;
   public double armoredTitanAttackDamage = 15.0;
   public double armoredTitanKickDamage = 20.0;
   public double femaleTitanAttackDamage = 20.0;
   public double femaleTitanKickDamage = 27.0;
   public double beastTitanAttackDamage = 15.0;
   public double beastTitanKickDamage = 100.0;
   public double colossalTitanAttackDamage = 30.0;
   public double colossalTitanSmashDamage = 50.0;
   public double shifterMeleePlayerDamageCap = 8.0;
   public double attackTitanAttackRange = 14.0;
   public double armoredTitanAttackRange = 14.0;
   public double femaleTitanAttackRange = 14.0;
   public double beastTitanKickRange = 70.0;
   public double colossalTitanAttackRange = 50.0;
   public double colossalTitanSmashRange = 60.0;
   public int titanSpawnWeight = 15;
   public int smallTitanSpawnWeight = 10;
   public int smallTitan2SpawnWeight = 12;
   public int fritzTitanSpawnWeight = 10;
   public int titanBeardSpawnWeight = 10;
   public int titanTropicalSpawnWeight = 10;
   public int abnormalTitanSpawnWeight = 10;
   public int crawlingAbnormalTitanSpawnWeight = 3;
   public int connieFatherSpawnWeight = 10;
   public int baseTitanSpawnCap = 8;
   public int extraTitansPerPlayer = 4;
   public int femaleCrystalDurationSeconds = 60;
   public float eldianWeight = 42.0F;
   public float marleyanWeight = 42.0F;
   public float ackermanWeight = 10.0F;
   public float royalWeight = 6.0F;
   public double bladeAirSwingRange = 1.0;
   public double ackermanBladeAirSwingRange = 1.5;
   public double ackermanAwakenedBladeAirSwingRange = 2.0;
   public double beastRockDamage = 35.0;
   public int beastRockCount = 16;
   public double beastRockSpreadDegrees = 16.0;
   public int paradisWallHeight = 60;
   public boolean enableVillagesPathsWalls = true;
   public boolean enableReturnPillarGeneration = true;
   public int ymirCurseDurationDays = 13;
   private final List<String> violations = new ArrayList<>();

   public static ModConfig get() {
      if (INSTANCE == null) {
         load();
      }

      return INSTANCE;
   }

   public static float capPlayerMelee(Entity target, float damage) {
      return target instanceof PlayerEntity ? (float)Math.min((double)damage, get().shifterMeleePlayerDamageCap) : damage;
   }

   public static Path getConfigPath() {
      return CONFIG_PATH;
   }

   private double clamp(String name, double value, double min, double max) {
      if (!(value < min) && !(value > max) && !Double.isNaN(value)) {
         return value;
      } else {
         double fixed = Double.isNaN(value) ? min : Math.min(max, Math.max(min, value));
         this.violations.add(name + " = " + value + " outside [" + min + ", " + max + "], clamped to " + fixed);
         return fixed;
      }
   }

   private float clampF(String name, float value, double min, double max) {
      return (float)this.clamp(name, value, min, max);
   }

   private int clampI(String name, int value, int min, int max) {
      if (value >= min && value <= max) {
         return value;
      } else {
         int fixed = Math.min(max, Math.max(min, value));
         this.violations.add(name + " = " + value + " outside [" + min + ", " + max + "], clamped to " + fixed);
         return fixed;
      }
   }

   private long clampL(String name, long value, long min, long max) {
      if (value >= min && value <= max) {
         return value;
      } else {
         long fixed = Math.min(max, Math.max(min, value));
         this.violations.add(name + " = " + value + " outside [" + min + ", " + max + "], clamped to " + fixed);
         return fixed;
      }
   }

   private List<String> clampAll() {
      this.violations.clear();
      this.dualHookEaseTime = this.clampL("dualHookEaseTime", this.dualHookEaseTime, 0L, 60000L);
      this.gasTickInterval = this.clampI("gasTickInterval", this.gasTickInterval, 1, 100);
      this.gasConsumptionNormal = this.clampI("gasConsumptionNormal", this.gasConsumptionNormal, 0, 20);
      this.gasConsumptionBoost = this.clampI("gasConsumptionBoost", this.gasConsumptionBoost, 0, 20);
      this.basePullSpeed = this.clamp("basePullSpeed", this.basePullSpeed, 0.01, 2.0);
      this.dualHookPullMultiplier = this.clamp("dualHookPullMultiplier", this.dualHookPullMultiplier, 0.1, 6.0);
      this.dualHookBoostPullMultiplier = this.clamp("dualHookBoostPullMultiplier", this.dualHookBoostPullMultiplier, 0.1, 6.0);
      this.orbitPullMultiplier = this.clamp("orbitPullMultiplier", this.orbitPullMultiplier, 0.1, 6.0);
      this.baseOrbitSpeed = this.clamp("baseOrbitSpeed", this.baseOrbitSpeed, 0.01, 2.0);
      this.dualHookOrbitMultiplier = this.clamp("dualHookOrbitMultiplier", this.dualHookOrbitMultiplier, 0.1, 6.0);
      this.upwardLift = this.clamp("upwardLift", this.upwardLift, 0.0, 1.0);
      this.boostPullMultiplier = this.clamp("boostPullMultiplier", this.boostPullMultiplier, 0.1, 6.0);
      this.boostOrbitMultiplier = this.clamp("boostOrbitMultiplier", this.boostOrbitMultiplier, 0.1, 6.0);
      this.boostRampRate = this.clamp("boostRampRate", this.boostRampRate, 1.0E-4, 1.0);
      this.boostDecayRate = this.clamp("boostDecayRate", this.boostDecayRate, 1.0E-4, 1.0);
      this.maxHookDistance = this.clamp("maxHookDistance", this.maxHookDistance, 10.0, 500.0);
      this.momentumPreserveTime = this.clampL("momentumPreserveTime", this.momentumPreserveTime, 0L, 60000L);
      this.flightSoundVelocityThreshold = this.clamp("flightSoundVelocityThreshold", this.flightSoundVelocityThreshold, 0.0, 10.0);
      this.attackMaxStamina = this.clampF("attackMaxStamina", this.attackMaxStamina, 100.0, 50000.0);
      this.armoredMaxStamina = this.clampF("armoredMaxStamina", this.armoredMaxStamina, 100.0, 50000.0);
      this.colossalMaxStamina = this.clampF("colossalMaxStamina", this.colossalMaxStamina, 100.0, 50000.0);
      this.femaleMaxStamina = this.clampF("femaleMaxStamina", this.femaleMaxStamina, 100.0, 50000.0);
      this.beastMaxStamina = this.clampF("beastMaxStamina", this.beastMaxStamina, 100.0, 50000.0);
      this.attackTitanHealth = this.clamp("attackTitanHealth", this.attackTitanHealth, 20.0, 5000.0);
      this.armoredTitanHealth = this.clamp("armoredTitanHealth", this.armoredTitanHealth, 20.0, 5000.0);
      this.colossalTitanHealth = this.clamp("colossalTitanHealth", this.colossalTitanHealth, 20.0, 5000.0);
      this.femaleTitanHealth = this.clamp("femaleTitanHealth", this.femaleTitanHealth, 20.0, 5000.0);
      this.beastTitanHealth = this.clamp("beastTitanHealth", this.beastTitanHealth, 20.0, 5000.0);
      this.unbackedTagGraceTicks = this.clampI("unbackedTagGraceTicks", this.unbackedTagGraceTicks, 0, 6000);
      this.attackTitanNapeHits = this.clampI("attackTitanNapeHits", this.attackTitanNapeHits, 1, 20);
      this.armoredTitanNapeHits = this.clampI("armoredTitanNapeHits", this.armoredTitanNapeHits, 1, 20);
      this.colossalTitanNapeHits = this.clampI("colossalTitanNapeHits", this.colossalTitanNapeHits, 1, 20);
      this.femaleTitanNapeHits = this.clampI("femaleTitanNapeHits", this.femaleTitanNapeHits, 1, 20);
      this.beastTitanNapeHits = this.clampI("beastTitanNapeHits", this.beastTitanNapeHits, 1, 20);
      this.warhammerTitanNapeHits = this.clampI("warhammerTitanNapeHits", this.warhammerTitanNapeHits, 1, 20);
      this.pureTitanWanderSpeed = this.clamp("pureTitanWanderSpeed", this.pureTitanWanderSpeed, 0.0, 2.0);
      this.pureTitanChaseSpeed = this.clamp("pureTitanChaseSpeed", this.pureTitanChaseSpeed, 0.0, 2.0);
      this.smallTitanWanderSpeed = this.clamp("smallTitanWanderSpeed", this.smallTitanWanderSpeed, 0.0, 2.0);
      this.smallTitanChaseSpeed = this.clamp("smallTitanChaseSpeed", this.smallTitanChaseSpeed, 0.0, 2.0);
      this.smallTitan2WanderSpeed = this.clamp("smallTitan2WanderSpeed", this.smallTitan2WanderSpeed, 0.0, 2.0);
      this.smallTitan2ChaseSpeed = this.clamp("smallTitan2ChaseSpeed", this.smallTitan2ChaseSpeed, 0.0, 2.0);
      this.yellowTitanWanderSpeed = this.clamp("yellowTitanWanderSpeed", this.yellowTitanWanderSpeed, 0.0, 2.0);
      this.yellowTitanChaseSpeed = this.clamp("yellowTitanChaseSpeed", this.yellowTitanChaseSpeed, 0.0, 2.0);
      this.fritzTitanWanderSpeed = this.clamp("fritzTitanWanderSpeed", this.fritzTitanWanderSpeed, 0.0, 2.0);
      this.fritzTitanChaseSpeed = this.clamp("fritzTitanChaseSpeed", this.fritzTitanChaseSpeed, 0.0, 2.0);
      this.titanBeardWanderSpeed = this.clamp("titanBeardWanderSpeed", this.titanBeardWanderSpeed, 0.0, 2.0);
      this.titanBeardChaseSpeed = this.clamp("titanBeardChaseSpeed", this.titanBeardChaseSpeed, 0.0, 2.0);
      this.titanTropicalWanderSpeed = this.clamp("titanTropicalWanderSpeed", this.titanTropicalWanderSpeed, 0.0, 2.0);
      this.titanTropicalChaseSpeed = this.clamp("titanTropicalChaseSpeed", this.titanTropicalChaseSpeed, 0.0, 2.0);
      this.pureTitanEatingDamage = this.clamp("pureTitanEatingDamage", this.pureTitanEatingDamage, 0.0, 100.0);
      this.attackTitanAttackDamage = this.clamp("attackTitanAttackDamage", this.attackTitanAttackDamage, 0.0, 200.0);
      this.attackTitanKickDamage = this.clamp("attackTitanKickDamage", this.attackTitanKickDamage, 0.0, 200.0);
      this.armoredTitanAttackDamage = this.clamp("armoredTitanAttackDamage", this.armoredTitanAttackDamage, 0.0, 200.0);
      this.armoredTitanKickDamage = this.clamp("armoredTitanKickDamage", this.armoredTitanKickDamage, 0.0, 200.0);
      this.femaleTitanAttackDamage = this.clamp("femaleTitanAttackDamage", this.femaleTitanAttackDamage, 0.0, 200.0);
      this.femaleTitanKickDamage = this.clamp("femaleTitanKickDamage", this.femaleTitanKickDamage, 0.0, 200.0);
      this.beastTitanAttackDamage = this.clamp("beastTitanAttackDamage", this.beastTitanAttackDamage, 0.0, 200.0);
      this.beastTitanKickDamage = this.clamp("beastTitanKickDamage", this.beastTitanKickDamage, 0.0, 400.0);
      this.colossalTitanAttackDamage = this.clamp("colossalTitanAttackDamage", this.colossalTitanAttackDamage, 0.0, 200.0);
      this.colossalTitanSmashDamage = this.clamp("colossalTitanSmashDamage", this.colossalTitanSmashDamage, 0.0, 400.0);
      this.shifterMeleePlayerDamageCap = this.clamp("shifterMeleePlayerDamageCap", this.shifterMeleePlayerDamageCap, 0.0, 100.0);
      this.attackTitanAttackRange = this.clamp("attackTitanAttackRange", this.attackTitanAttackRange, 1.0, 100.0);
      this.armoredTitanAttackRange = this.clamp("armoredTitanAttackRange", this.armoredTitanAttackRange, 1.0, 100.0);
      this.femaleTitanAttackRange = this.clamp("femaleTitanAttackRange", this.femaleTitanAttackRange, 1.0, 100.0);
      this.beastTitanKickRange = this.clamp("beastTitanKickRange", this.beastTitanKickRange, 1.0, 200.0);
      this.colossalTitanAttackRange = this.clamp("colossalTitanAttackRange", this.colossalTitanAttackRange, 1.0, 200.0);
      this.colossalTitanSmashRange = this.clamp("colossalTitanSmashRange", this.colossalTitanSmashRange, 1.0, 200.0);
      this.titanSpawnWeight = this.clampI("titanSpawnWeight", this.titanSpawnWeight, 0, 1000);
      this.smallTitanSpawnWeight = this.clampI("smallTitanSpawnWeight", this.smallTitanSpawnWeight, 0, 1000);
      this.smallTitan2SpawnWeight = this.clampI("smallTitan2SpawnWeight", this.smallTitan2SpawnWeight, 0, 1000);
      this.fritzTitanSpawnWeight = this.clampI("fritzTitanSpawnWeight", this.fritzTitanSpawnWeight, 0, 1000);
      this.titanBeardSpawnWeight = this.clampI("titanBeardSpawnWeight", this.titanBeardSpawnWeight, 0, 1000);
      this.titanTropicalSpawnWeight = this.clampI("titanTropicalSpawnWeight", this.titanTropicalSpawnWeight, 0, 1000);
      this.abnormalTitanSpawnWeight = this.clampI("abnormalTitanSpawnWeight", this.abnormalTitanSpawnWeight, 0, 1000);
      this.crawlingAbnormalTitanSpawnWeight = this.clampI("crawlingAbnormalTitanSpawnWeight", this.crawlingAbnormalTitanSpawnWeight, 0, 1000);
      this.connieFatherSpawnWeight = this.clampI("connieFatherSpawnWeight", this.connieFatherSpawnWeight, 0, 1000);
      this.baseTitanSpawnCap = this.clampI("baseTitanSpawnCap", this.baseTitanSpawnCap, 0, 500);
      this.extraTitansPerPlayer = this.clampI("extraTitansPerPlayer", this.extraTitansPerPlayer, 0, 100);
      this.femaleCrystalDurationSeconds = this.clampI("femaleCrystalDurationSeconds", this.femaleCrystalDurationSeconds, 1, 3600);
      this.eldianWeight = this.clampF("eldianWeight", this.eldianWeight, 0.0, 10000.0);
      this.marleyanWeight = this.clampF("marleyanWeight", this.marleyanWeight, 0.0, 10000.0);
      this.ackermanWeight = this.clampF("ackermanWeight", this.ackermanWeight, 0.0, 10000.0);
      this.royalWeight = this.clampF("royalWeight", this.royalWeight, 0.0, 10000.0);
      this.bladeAirSwingRange = this.clamp("bladeAirSwingRange", this.bladeAirSwingRange, 0.0, 20.0);
      this.ackermanBladeAirSwingRange = this.clamp("ackermanBladeAirSwingRange", this.ackermanBladeAirSwingRange, 0.0, 20.0);
      this.ackermanAwakenedBladeAirSwingRange = this.clamp("ackermanAwakenedBladeAirSwingRange", this.ackermanAwakenedBladeAirSwingRange, 0.0, 20.0);
      this.beastRockDamage = this.clamp("beastRockDamage", this.beastRockDamage, 0.0, 300.0);
      this.beastRockCount = this.clampI("beastRockCount", this.beastRockCount, 1, 128);
      this.beastRockSpreadDegrees = this.clamp("beastRockSpreadDegrees", this.beastRockSpreadDegrees, 0.0, 180.0);
      this.paradisWallHeight = this.clampI("paradisWallHeight", this.paradisWallHeight, 10, 200);
      this.ymirCurseDurationDays = this.clampI("ymirCurseDurationDays", this.ymirCurseDurationDays, 1, 10000);
      return new ArrayList<>(this.violations);
   }

   public static void load() {
      if (Files.exists(CONFIG_PATH)) {
         try {
            String json = Files.readString(CONFIG_PATH);
            INSTANCE = (ModConfig)GSON.fromJson(json, ModConfig.class);
            if (INSTANCE == null) {
               INSTANCE = new ModConfig();
            }

            DannysAot.LOGGER.info("Loaded mod config from {}", CONFIG_PATH);
         } catch (IOException var1) {
            DannysAot.LOGGER.error("Failed to load mod config, using defaults", var1);
            INSTANCE = new ModConfig();
         }

         validateLoaded();
         save();
      } else {
         INSTANCE = new ModConfig();
         save();
         DannysAot.LOGGER.info("Created default mod config at {}", CONFIG_PATH);
      }
   }

   private static void validateLoaded() {
      List<String> problems = INSTANCE.clampAll();
      if (!problems.isEmpty()) {
         for (String problem : problems) {
            DannysAot.LOGGER.warn("[dannys-aot] config: {}", problem);
         }

         if (INSTANCE.strictConfig) {
            throw new ModConfig.InvalidConfigException(
               "Danny's AoT: "
                  + problems.size()
                  + " config value(s) outside their designed range and strictConfig is enabled. Fix config/dannys-aot.json or set strictConfig to false. First problem: "
                  + problems.get(0)
            );
         } else {
            DannysAot.LOGGER
               .warn(
                  "[dannys-aot] {} config value(s) were outside their designed range and have been clamped. Set strictConfig=true to make this a startup error instead.",
                  problems.size()
               );
         }
      }
   }

   public static void save() {
      try {
         Files.createDirectories(CONFIG_PATH.getParent());
         StringBuilder sb = new StringBuilder();
         sb.append("{\n");
         sb.append("  \"schemaVersion\": ").append(INSTANCE.schemaVersion).append(",\n");
         sb.append("  \"strictConfig\": ").append(INSTANCE.strictConfig).append(",\n");
         sb.append("\n");
         sb.append("  \"dualHookEaseTime\": ").append(INSTANCE.dualHookEaseTime).append(",\n");
         sb.append("\n");
         sb.append("  \"gasTickInterval\": ").append(INSTANCE.gasTickInterval).append(",\n");
         sb.append("  \"gasConsumptionNormal\": ").append(INSTANCE.gasConsumptionNormal).append(",\n");
         sb.append("  \"gasConsumptionBoost\": ").append(INSTANCE.gasConsumptionBoost).append(",\n");
         sb.append("\n");
         sb.append("  \"basePullSpeed\": ").append(INSTANCE.basePullSpeed).append(",\n");
         sb.append("  \"dualHookPullMultiplier\": ").append(INSTANCE.dualHookPullMultiplier).append(",\n");
         sb.append("  \"dualHookBoostPullMultiplier\": ").append(INSTANCE.dualHookBoostPullMultiplier).append(",\n");
         sb.append("  \"orbitPullMultiplier\": ").append(INSTANCE.orbitPullMultiplier).append(",\n");
         sb.append("\n");
         sb.append("  \"baseOrbitSpeed\": ").append(INSTANCE.baseOrbitSpeed).append(",\n");
         sb.append("  \"dualHookOrbitMultiplier\": ").append(INSTANCE.dualHookOrbitMultiplier).append(",\n");
         sb.append("\n");
         sb.append("  \"upwardLift\": ").append(INSTANCE.upwardLift).append(",\n");
         sb.append("\n");
         sb.append("  \"boostPullMultiplier\": ").append(INSTANCE.boostPullMultiplier).append(",\n");
         sb.append("  \"boostOrbitMultiplier\": ").append(INSTANCE.boostOrbitMultiplier).append(",\n");
         sb.append("  \"boostRampRate\": ").append(INSTANCE.boostRampRate).append(",\n");
         sb.append("  \"boostDecayRate\": ").append(INSTANCE.boostDecayRate).append(",\n");
         sb.append("\n");
         sb.append("  \"maxHookDistance\": ").append(INSTANCE.maxHookDistance).append(",\n");
         sb.append("  \"momentumPreserveTime\": ").append(INSTANCE.momentumPreserveTime).append(",\n");
         sb.append("  \"flightSoundVelocityThreshold\": ").append(INSTANCE.flightSoundVelocityThreshold).append(",\n");
         sb.append("\n");
         sb.append("  \"attackMaxStamina\": ").append(INSTANCE.attackMaxStamina).append(",\n");
         sb.append("  \"armoredMaxStamina\": ").append(INSTANCE.armoredMaxStamina).append(",\n");
         sb.append("  \"colossalMaxStamina\": ").append(INSTANCE.colossalMaxStamina).append(",\n");
         sb.append("  \"femaleMaxStamina\": ").append(INSTANCE.femaleMaxStamina).append(",\n");
         sb.append("  \"beastMaxStamina\": ").append(INSTANCE.beastMaxStamina).append(",\n");
         sb.append("\n");
         sb.append("  \"attackTitanHealth\": ").append(INSTANCE.attackTitanHealth).append(",\n");
         sb.append("  \"armoredTitanHealth\": ").append(INSTANCE.armoredTitanHealth).append(",\n");
         sb.append("  \"colossalTitanHealth\": ").append(INSTANCE.colossalTitanHealth).append(",\n");
         sb.append("  \"femaleTitanHealth\": ").append(INSTANCE.femaleTitanHealth).append(",\n");
         sb.append("  \"beastTitanHealth\": ").append(INSTANCE.beastTitanHealth).append(",\n");
         sb.append("\n");
         sb.append("  \"grandfatherLegacyShifterTags\": ").append(INSTANCE.grandfatherLegacyShifterTags).append(",\n");
         sb.append("  \"unbackedTagGraceTicks\": ").append(INSTANCE.unbackedTagGraceTicks).append(",\n");
         sb.append("\n");
         sb.append("  \"attackTitanNapeHits\": ").append(INSTANCE.attackTitanNapeHits).append(",\n");
         sb.append("  \"armoredTitanNapeHits\": ").append(INSTANCE.armoredTitanNapeHits).append(",\n");
         sb.append("  \"colossalTitanNapeHits\": ").append(INSTANCE.colossalTitanNapeHits).append(",\n");
         sb.append("  \"femaleTitanNapeHits\": ").append(INSTANCE.femaleTitanNapeHits).append(",\n");
         sb.append("  \"beastTitanNapeHits\": ").append(INSTANCE.beastTitanNapeHits).append(",\n");
         sb.append("  \"warhammerTitanNapeHits\": ").append(INSTANCE.warhammerTitanNapeHits).append(",\n");
         sb.append("\n");
         sb.append("  \"pureTitanWanderSpeed\": ").append(INSTANCE.pureTitanWanderSpeed).append(",\n");
         sb.append("  \"pureTitanChaseSpeed\": ").append(INSTANCE.pureTitanChaseSpeed).append(",\n");
         sb.append("  \"smallTitanWanderSpeed\": ").append(INSTANCE.smallTitanWanderSpeed).append(",\n");
         sb.append("  \"smallTitanChaseSpeed\": ").append(INSTANCE.smallTitanChaseSpeed).append(",\n");
         sb.append("  \"smallTitan2WanderSpeed\": ").append(INSTANCE.smallTitan2WanderSpeed).append(",\n");
         sb.append("  \"smallTitan2ChaseSpeed\": ").append(INSTANCE.smallTitan2ChaseSpeed).append(",\n");
         sb.append("  \"yellowTitanWanderSpeed\": ").append(INSTANCE.yellowTitanWanderSpeed).append(",\n");
         sb.append("  \"yellowTitanChaseSpeed\": ").append(INSTANCE.yellowTitanChaseSpeed).append(",\n");
         sb.append("  \"fritzTitanWanderSpeed\": ").append(INSTANCE.fritzTitanWanderSpeed).append(",\n");
         sb.append("  \"fritzTitanChaseSpeed\": ").append(INSTANCE.fritzTitanChaseSpeed).append(",\n");
         sb.append("  \"titanBeardWanderSpeed\": ").append(INSTANCE.titanBeardWanderSpeed).append(",\n");
         sb.append("  \"titanBeardChaseSpeed\": ").append(INSTANCE.titanBeardChaseSpeed).append(",\n");
         sb.append("  \"titanTropicalWanderSpeed\": ").append(INSTANCE.titanTropicalWanderSpeed).append(",\n");
         sb.append("  \"titanTropicalChaseSpeed\": ").append(INSTANCE.titanTropicalChaseSpeed).append(",\n");
         sb.append("\n");
         sb.append("  \"pureTitanEatingDamage\": ").append(INSTANCE.pureTitanEatingDamage).append(",\n");
         sb.append("  \"canStunWhileGrabbed\": ").append(INSTANCE.canStunWhileGrabbed).append(",\n");
         sb.append("\n");
         sb.append("  \"attackTitanAttackDamage\": ").append(INSTANCE.attackTitanAttackDamage).append(",\n");
         sb.append("  \"attackTitanKickDamage\": ").append(INSTANCE.attackTitanKickDamage).append(",\n");
         sb.append("  \"armoredTitanAttackDamage\": ").append(INSTANCE.armoredTitanAttackDamage).append(",\n");
         sb.append("  \"armoredTitanKickDamage\": ").append(INSTANCE.armoredTitanKickDamage).append(",\n");
         sb.append("  \"femaleTitanAttackDamage\": ").append(INSTANCE.femaleTitanAttackDamage).append(",\n");
         sb.append("  \"femaleTitanKickDamage\": ").append(INSTANCE.femaleTitanKickDamage).append(",\n");
         sb.append("  \"beastTitanAttackDamage\": ").append(INSTANCE.beastTitanAttackDamage).append(",\n");
         sb.append("  \"beastTitanKickDamage\": ").append(INSTANCE.beastTitanKickDamage).append(",\n");
         sb.append("  \"colossalTitanAttackDamage\": ").append(INSTANCE.colossalTitanAttackDamage).append(",\n");
         sb.append("  \"shifterMeleePlayerDamageCap\": ").append(INSTANCE.shifterMeleePlayerDamageCap).append(",\n");
         sb.append("  \"colossalTitanSmashDamage\": ").append(INSTANCE.colossalTitanSmashDamage).append(",\n");
         sb.append("\n");
         sb.append("  \"attackTitanAttackRange\": ").append(INSTANCE.attackTitanAttackRange).append(",\n");
         sb.append("  \"armoredTitanAttackRange\": ").append(INSTANCE.armoredTitanAttackRange).append(",\n");
         sb.append("  \"femaleTitanAttackRange\": ").append(INSTANCE.femaleTitanAttackRange).append(",\n");
         sb.append("  \"beastTitanKickRange\": ").append(INSTANCE.beastTitanKickRange).append(",\n");
         sb.append("  \"colossalTitanAttackRange\": ").append(INSTANCE.colossalTitanAttackRange).append(",\n");
         sb.append("  \"colossalTitanSmashRange\": ").append(INSTANCE.colossalTitanSmashRange).append(",\n");
         sb.append("\n");
         sb.append("  \"titanSpawnWeight\": ").append(INSTANCE.titanSpawnWeight).append(",\n");
         sb.append("  \"smallTitanSpawnWeight\": ").append(INSTANCE.smallTitanSpawnWeight).append(",\n");
         sb.append("  \"smallTitan2SpawnWeight\": ").append(INSTANCE.smallTitan2SpawnWeight).append(",\n");
         sb.append("  \"fritzTitanSpawnWeight\": ").append(INSTANCE.fritzTitanSpawnWeight).append(",\n");
         sb.append("  \"titanBeardSpawnWeight\": ").append(INSTANCE.titanBeardSpawnWeight).append(",\n");
         sb.append("  \"titanTropicalSpawnWeight\": ").append(INSTANCE.titanTropicalSpawnWeight).append(",\n");
         sb.append("  \"abnormalTitanSpawnWeight\": ").append(INSTANCE.abnormalTitanSpawnWeight).append(",\n");
         sb.append("  \"crawlingAbnormalTitanSpawnWeight\": ").append(INSTANCE.crawlingAbnormalTitanSpawnWeight).append(",\n");
         sb.append("  \"connieFatherSpawnWeight\": ").append(INSTANCE.connieFatherSpawnWeight).append(",\n");
         sb.append("  \"baseTitanSpawnCap\": ").append(INSTANCE.baseTitanSpawnCap).append(",\n");
         sb.append("  \"extraTitansPerPlayer\": ").append(INSTANCE.extraTitansPerPlayer).append(",\n");
         sb.append("\n");
         sb.append("  \"eldianWeight\": ").append(INSTANCE.eldianWeight).append(",\n");
         sb.append("  \"marleyanWeight\": ").append(INSTANCE.marleyanWeight).append(",\n");
         sb.append("  \"ackermanWeight\": ").append(INSTANCE.ackermanWeight).append(",\n");
         sb.append("  \"royalWeight\": ").append(INSTANCE.royalWeight).append(",\n");
         sb.append("\n");
         sb.append("  \"bladeAirSwingRange\": ").append(INSTANCE.bladeAirSwingRange).append(",\n");
         sb.append("  \"ackermanBladeAirSwingRange\": ").append(INSTANCE.ackermanBladeAirSwingRange).append(",\n");
         sb.append("  \"ackermanAwakenedBladeAirSwingRange\": ").append(INSTANCE.ackermanAwakenedBladeAirSwingRange).append(",\n");
         sb.append("\n");
         sb.append("  \"beastRockDamage\": ").append(INSTANCE.beastRockDamage).append(",\n");
         sb.append("  \"beastRockCount\": ").append(INSTANCE.beastRockCount).append(",\n");
         sb.append("  \"beastRockSpreadDegrees\": ").append(INSTANCE.beastRockSpreadDegrees).append(",\n");
         sb.append("\n");
         sb.append("  \"femaleCrystalDurationSeconds\": ").append(INSTANCE.femaleCrystalDurationSeconds).append(",\n");
         sb.append("\n");
         sb.append("  \"paradisWallHeight\": ").append(INSTANCE.paradisWallHeight).append(",\n");
         sb.append("  \"enableVillagesPathsWalls\": ").append(INSTANCE.enableVillagesPathsWalls).append(",\n");
         sb.append("  \"enableReturnPillarGeneration\": ").append(INSTANCE.enableReturnPillarGeneration).append(",\n");
         sb.append("\n");
         sb.append("  \"ymirCurseDurationDays\": ").append(INSTANCE.ymirCurseDurationDays).append("\n");
         sb.append("}");
         Files.writeString(CONFIG_PATH, sb.toString());
      } catch (IOException var1) {
         DannysAot.LOGGER.error("Failed to save mod config", var1);
      }
   }

   public static void reload() {
      INSTANCE = null;
      load();
   }

   public static class InvalidConfigException extends RuntimeException {
      public InvalidConfigException(String message) {
         super(message);
      }
   }
}
