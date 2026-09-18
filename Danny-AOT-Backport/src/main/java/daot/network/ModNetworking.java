package daot.network;

import daot.APGGunItem;
import daot.APGProjectileEntity;
import daot.AbnormalTitanEntity;
import daot.ArmoredTitanEntity;
import daot.ArmoredTitanEyeEntity;
import daot.ArmoredTitanGrabEntity;
import daot.ArmoredTitanLegEntity;
import daot.ArmoredTitanNapeEntity;
import daot.AtrainSpeedTracker;
import daot.AttackTitanEntity;
import daot.AttackTitanEyeEntity;
import daot.AttackTitanGrabEntity;
import daot.AttackTitanNapeEntity;
import daot.AwakenedPowerTracker;
import daot.BeastTitanEntity;
import daot.BeastTitanEyeEntity;
import daot.BeastTitanGrabEntity;
import daot.BeastTitanNapeEntity;
import daot.BladeAttackTracker;
import daot.BladeBlockTracker;
import daot.BladeComponentItem;
import daot.BladeItem;
import daot.BloodlineData;
import daot.BloodlineType;
import daot.BloodmoonState;
import daot.BreachManager;
import daot.ButcherGrabHandler;
import daot.ButcherTentacleServerHandler;
import daot.CartShifterTitanEntity;
import daot.CloakItem;
import daot.ColossalTitanEntity;
import daot.ColossalTitanEyeEntity;
import daot.ColossalTitanHandEntity;
import daot.ColossalTitanNapeEntity;
import daot.ConnieFatherEntity;
import daot.ConnieFatherEyeEntity;
import daot.ConnieFatherNapeEntity;
import daot.CrawlerTitanEntity;
import daot.CrawlerTitanEyeEntity;
import daot.CrawlerTitanNapeEntity;
import daot.CrawlingAbnormalTitanEntity;
import daot.DannysAot;
import daot.DefeatedCarryTracker;
import daot.FemaleCrystalShellEntity;
import daot.FemaleTitanEntity;
import daot.FemaleTitanEyeEntity;
import daot.FemaleTitanGrabEntity;
import daot.FemaleTitanNapeEntity;
import daot.FlareCartridgeItem;
import daot.FlareGunItem;
import daot.FogEventState;
import daot.FoundingTitanEntity;
import daot.FritzTitanEntity;
import daot.FritzTitanEyeEntity;
import daot.FritzTitanNapeEntity;
import daot.GasCanisterItem;
import daot.GeassManager;
import daot.GibEntity;
import daot.GrantProvenance;
import daot.HandcuffsTracker;
import daot.HomelanderBloodTracker;
import daot.HomelanderFlightServerHandler;
import daot.HomelanderGrabServerHandler;
import daot.HoodTracker;
import daot.LsoCompat;
import daot.MCACompat;
import daot.ModCommands;
import daot.ModConfig;
import daot.ModEffects;
import daot.ModSounds;
import daot.OgreHealAbility;
import daot.OgreShifterTitanEntity;
import daot.OgreTitanEntity;
import daot.OgreTitanNapeEntity;
import daot.PowerAuthority;
import daot.PowerDamageMarker;
import daot.PushAuraTracker;
import daot.RealisticResourceCaps;
import daot.SadTitanEntity;
import daot.ServerHookTracker;
import daot.ShifterDodgeManager;
import daot.ShifterMarkTracker;
import daot.ShifterTitan;
import daot.SmallTitan2Entity;
import daot.SmallTitan2EyeEntity;
import daot.SmallTitan2NapeEntity;
import daot.SmallTitanEntity;
import daot.SmallTitanEyeEntity;
import daot.SmallTitanNapeEntity;
import daot.SoldierboyNeckGrabServerHandler;
import daot.StrwsAimServerState;
import daot.StrwsBlockEntity;
import daot.StrwsMultiblock;
import daot.StrwsRestraintTracker;
import daot.TestShifterTitanEntity;
import daot.ThunderSpearEntity;
import daot.ThunderSpearItem;
import daot.TitanBeardEntity;
import daot.TitanDashTracker;
import daot.TitanDummyEntity;
import daot.TitanDummyEyeEntity;
import daot.TitanDummyNapeEntity;
import daot.TitanEntity;
import daot.TitanEyeEntity;
import daot.TitanNapeEntity;
import daot.TitanPowerData;
import daot.TitanPowerHelper;
import daot.TitanPowerType;
import daot.TitanTossManager;
import daot.TitanTropicalEntity;
import daot.TranslucentTracker;
import daot.TripleTTitanEntity;
import daot.VillagerTransformTracker;
import daot.WarhammerTitanEntity;
import daot.WarhammerTitanEyeEntity;
import daot.WarhammerTitanNapeEntity;
import daot.YellowTitanEntity;
import daot.ZekesGlassesItem;
import daot.advancement.ModCriteriaTriggers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import daot.compat.DamageEvents.AfterDamage;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AfterRespawn;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.Load;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import daot.compat.network.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import daot.compat.network.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoItem;

public class ModNetworking {
   public static final String COLOSSAL_SHIFTER_TAG = "colossal";
   public static final String ATTACK_SHIFTER_TAG = "attack";
   public static final String ARMORED_SHIFTER_TAG = "armored";
   public static final String BEAST_SHIFTER_TAG = "beast";
   public static final String FEMALE_SHIFTER_TAG = "female";
   public static final String WARHAMMER_SHIFTER_TAG = "warhammer";
   public static final String FOUNDER_SHIFTER_TAG = "founder";
   public static final String TRIPLE_T_SHIFTER_TAG = "triple_t";
   public static final String OGRE_SHIFTER_TAG = "ogre_shifter";
   public static final String JAW_SHIFTER_TAG = "jaw";
   public static final String CART_SHIFTER_TAG = "cart_shifter";
   public static final String STEALTH_TAG = "titan_stealth";
   public static final String TITAN_BLOODLINE_TAG = "titan_bloodline";
   private static final Map<UUID, int[]> previousHookedEntities = new HashMap<>();
   private static final Map<UUID, Integer> stealthInvisibilityTimers = new HashMap<>();
   private static final Map<UUID, String> pureTitanOwnerNames = new HashMap<>();
   private static final Set<UUID> awakenActive = new HashSet<>();
   private static final List<ModNetworking.PendingCommand> pendingCommands = new ArrayList<>();
   private static final Map<UUID, ModNetworking.PendingShift> pendingShifts = new HashMap<>();
   private static final Map<UUID, Integer> pendingForceShiftHalfHealth = new HashMap<>();
   private static final int BITE_DURATION_TICKS = 21;
   private static final Map<UUID, ModNetworking.PendingBite> pendingBites = new HashMap<>();
   private static final Map<UUID, ModNetworking.AscendingPlayer> ascendingPlayers = new HashMap<>();
   private static final Map<UUID, Vec3d> nukeFreezeAnchors = new HashMap<>();
   private static final Map<UUID, Long> bladeChargeExpiry = new HashMap<>();
   private static final Identifier BLADE_CHARGE_ID = new Identifier("dannys-aot", "blade_charge_bonus");
   private static final Map<UUID, Float> playerStamina = new HashMap<>();
   private static final Map<UUID, Float> playerMaxStamina = new HashMap<>();
   public static final float MAX_STAMINA = 2000.0F;
   private static final Set<UUID> ogreStaminaImmune = new HashSet<>();
   private static final Identifier ACKERMAN_HEALTH_ID = new Identifier("dannys-aot", "ackerman_health");
   private static final Identifier ACKERMAN_DAMAGE_ID = new Identifier("dannys-aot", "ackerman_damage");
   private static final Identifier ATRAIN_SAFE_FALL_ID = new Identifier("dannys-aot", "atrain_safe_fall");
   private static final Identifier OGRE_SHIFTER_HEALTH_ID = new Identifier("dannys-aot", "ogre_shifter_health");
   private static final Identifier TITAN_BLOODLINE_SCALE_ID = new Identifier("dannys-aot", "titan_bloodline_scale");
   private static final float BASE_DRAIN_PER_TICK = 0.119F;
   private static final float ATTACK_DRAIN = 5.0F;
   private static final float ABILITY_DRAIN = 15.0F;
   private static final float ARMORED_CHARGE_DRAIN_PER_TICK = 0.75F;
   private static final float REGEN_PER_TICK = 0.238F;
   private static final float HEALING_DRAIN_PER_TICK = 0.12F;
   public static final int TRANSFORMATION_TICKS = 95;
   private static final double ASCEND_SPEED = 2.5;
   private static int lastBroadcastBloodmoonTrack = -1;
   private static final float KINETIC_ODM_THRESHOLD = 1.2F;
   private static final float KINETIC_ODM_SCALE = 7.0F;
   private static final float KINETIC_ODM_MAX = 20.0F;
   private static final Map<UUID, Long> stopCommandCooldowns = new HashMap<>();
   private static final Map<UUID, Long> continueCommandCooldowns = new HashMap<>();
   private static final long COMMAND_COOLDOWN_MS = 2000L;
   private static final Map<UUID, Integer> roarBlockTicks = new HashMap<>();
   private static final Map<UUID, Integer> awakenCooldownTicks = new HashMap<>();
   private static final int AWAKEN_COOLDOWN = 300;
   private static final Map<UUID, Long> nonRoyalRejectCooldowns = new HashMap<>();
   private static final long NON_ROYAL_REJECT_COOLDOWN_MS = 1000L;
   private static final Map<UUID, Integer> activeStopHeartbeats = new HashMap<>();
   private static final int HEARTBEAT_INTERVAL = 14;
   private static final Map<UUID, ModNetworking.TargetMode> targetModes = new HashMap<>();
   private static final Map<UUID, Integer> highlightedTargetIds = new HashMap<>();
   private static final Map<UUID, UUID> activeTargetUUIDs = new HashMap<>();
   private static final Map<UUID, String> activeTargetNames = new HashMap<>();
   private static final Map<UUID, ModNetworking.FounderTarget> founderTargets = new HashMap<>();
   private static final Set<UUID> founderStopActive = new HashSet<>();
   private static final Set<UUID> founderLocalMode = new HashSet<>();
   public static final String FOUNDER_SUMMONED_TAG = "dannysaot_founder_summoned";
   private static final Map<UUID, List<UUID>> founderGuards = new HashMap<>();
   private static final float SOLDIERBOY_MIN_FIRE_THRESHOLD = 0.2F;
   private static final Set<UUID> activeChestLaserFirers = ConcurrentHashMap.newKeySet();
   private static final Map<UUID, Long> lastChestLaserTick = new ConcurrentHashMap<>();
   private static final long CHEST_LASER_TIMEOUT_TICKS = 3L;
   private static final double BLAST_RADIUS_MIN = 4.0;
   private static final double BLAST_RADIUS_MAX = 12.0;
   private static final float BLAST_DAMAGE_PEAK = 30.0F;
   private static final int BLAST_BLOCK_CAP = 4000;
   private static final double BLAST_BLOCK_FORWARD = 0.8;
   private static final double BLAST_BLOCK_UP = 0.5;
   private static final float BLAST_FIRE_SECONDS = 5.0F;
   private static final double CHEST_LASER_RANGE_MIN = 80.0;
   private static final double CHEST_LASER_RANGE_MAX = 80.0;
   private static final double CHEST_LASER_RADIUS_MIN = 2.5;
   private static final double CHEST_LASER_RADIUS_MAX = 2.5;
   private static final float CHEST_LASER_DAMAGE_PEAK = 8.0F;
   private static final int CHEST_LASER_BLOCK_CAP = 80;
   private static final Set<UUID> activeLaserFirers = ConcurrentHashMap.newKeySet();
   private static final Map<UUID, Long> lastLaserPayloadTick = new ConcurrentHashMap<>();
   private static final Map<UUID, Vec3d> lastLaserDir = new ConcurrentHashMap<>();
   private static final int LASER_SUBSAMPLE_COUNT = 5;
   private static final long LASER_TIMEOUT_TICKS = 3L;
   private static final Set<UUID> wasPowerDisabledLastTick = ConcurrentHashMap.newKeySet();
   private static final int POWER_REGEN_DURATION = 600;
   private static final int POWER_REGEN_REFRESH_THRESHOLD = 100;
   private static final double LASER_EYE_OFFSET = 0.15;
   private static final double LASER_RANGE = 80.0;
   private static final double LASER_BEAM_RADIUS = 0.6;
   private static final float LASER_DAMAGE_PER_TICK = 12.0F;
   private static final float LASER_FIRE_SECONDS = 5.0F;
   private static final double LASER_BLOCK_STEP = 0.5;
   private static final int LASER_BLOCKS_PER_TICK_CAP = 200;
   private static final int APG_FIRE_MASK_MAIN = 1;
   private static final int APG_FIRE_MASK_OFF = 2;
   private static final HashMap<UUID, Long> apgLastFireTick = new HashMap<>();
   private static final long APG_RELOAD_COOLDOWN_TICKS = 13L;
   private static final double APG_HITSCAN_RANGE = 160.0;
   private static final HashMap<UUID, Long> flareGunCooldowns = new HashMap<>();

   public static boolean isFounder(Entity entity) {
      return entity instanceof PlayerEntity player && player.getCommandTags().contains("founder");
   }

   public static float getMaxStaminaForPlayer(ServerPlayerEntity player) {
      ModConfig config = ModConfig.get();
      if (player.getCommandTags().contains("attack")) {
         return config.attackMaxStamina;
      } else if (player.getCommandTags().contains("armored")) {
         return config.armoredMaxStamina;
      } else if (player.getCommandTags().contains("colossal")) {
         return config.colossalMaxStamina;
      } else if (player.getCommandTags().contains("female")) {
         return config.femaleMaxStamina;
      } else if (player.getCommandTags().contains("beast")) {
         return config.beastMaxStamina;
      } else if (player.getCommandTags().contains("warhammer")) {
         return config.attackMaxStamina;
      } else if (player.getCommandTags().contains("founder")) {
         return config.attackMaxStamina;
      } else if (player.getCommandTags().contains("triple_t")) {
         return config.attackMaxStamina;
      } else if (player.getCommandTags().contains("ogre_shifter")) {
         return config.attackMaxStamina;
      } else if (player.getCommandTags().contains("jaw")) {
         return config.attackMaxStamina;
      } else {
         return player.getCommandTags().contains("cart_shifter") ? config.attackMaxStamina : 2000.0F;
      }
   }

   public static float getMaxStaminaForUUID(UUID playerUUID) {
      return playerMaxStamina.getOrDefault(playerUUID, 2000.0F);
   }

   public static float getCurrentStamina(UUID playerUUID, float defaultMax) {
      return playerStamina.getOrDefault(playerUUID, defaultMax);
   }

   public static void forceShiftPlayer(ServerPlayerEntity player) {
      if (player != null && !player.isRemoved()) {
         Set<String> tags = player.getCommandTags();
         ModNetworking.ShifterType type;
         if (tags.contains("jaw")) {
            type = ModNetworking.ShifterType.JAW;
         } else if (tags.contains("cart_shifter")) {
            type = ModNetworking.ShifterType.CART;
         } else if (tags.contains("ogre_shifter")) {
            type = ModNetworking.ShifterType.OGRE_SHIFTER;
         } else if (tags.contains("founder")) {
            type = ModNetworking.ShifterType.FOUNDING;
         } else if (tags.contains("triple_t")) {
            type = ModNetworking.ShifterType.TRIPLE_T;
         } else if (tags.contains("attack")) {
            type = ModNetworking.ShifterType.ATTACK;
         } else if (tags.contains("female")) {
            type = ModNetworking.ShifterType.FEMALE;
         } else if (tags.contains("armored")) {
            type = ModNetworking.ShifterType.ARMORED;
         } else if (tags.contains("beast")) {
            type = ModNetworking.ShifterType.BEAST;
         } else if (tags.contains("warhammer")) {
            type = ModNetworking.ShifterType.WARHAMMER;
         } else {
            if (!tags.contains("colossal")) {
               return;
            }

            type = ModNetworking.ShifterType.COLOSSAL;
         }

         ServerWorld level = player.getServerWorld();
         double x = player.getX();
         double y = player.getY();
         double z = player.getZ();
         float yaw = player.getYaw();
         float playerMax = getMaxStaminaForPlayer(player);
         float newStamina = playerMax * 0.3F;
         playerMaxStamina.put(player.getUuid(), playerMax);
         playerStamina.put(player.getUuid(), newStamina);
         boolean hasBeast = tags.contains("beast");
         boolean hasFounding = tags.contains("founder");
         ServerPlayNetworking.send(player, new StaminaSyncPayload(newStamina, playerMax, hasBeast, hasFounding));
         switch (type) {
            case COLOSSAL:
               spawnColossalTitan(player, level, x, y, z, yaw);
               break;
            case ATTACK:
               spawnAttackTitan(player, level, x, y, z, yaw);
               break;
            case WARHAMMER:
               spawnWarhammerTitan(player, level, x, y, z, yaw);
               break;
            case ARMORED:
               spawnArmoredTitan(player, level, x, y, z, yaw);
               break;
            case BEAST:
               spawnBeastTitan(player, level, x, y, z, yaw);
               break;
            case FEMALE:
               spawnFemaleTitan(player, level, x, y, z, yaw);
            case PURE_TITAN:
            default:
               break;
            case FOUNDING:
               spawnFoundingTitan(player, level, x, y, z, yaw);
               break;
            case TRIPLE_T:
               spawnTripleTTitan(player, level, x, y, z, yaw);
               break;
            case OGRE_SHIFTER:
               spawnOgreShifterTitan(player, level, x, y, z, yaw);
               break;
            case JAW:
               spawnTestShifterTitan(player, level, x, y, z, yaw);
               break;
            case CART:
               spawnCartShifterTitan(player, level, x, y, z, yaw);
         }

         ModNetworking.AscendingPlayer ascending = ascendingPlayers.get(player.getUuid());
         if (ascending != null && ascending.titan() != null) {
            pendingForceShiftHalfHealth.put(ascending.titan().getUuid(), 2);
         }
      }
   }

   private static void tickPendingForceShiftHalfHealth(MinecraftServer server) {
      if (!pendingForceShiftHalfHealth.isEmpty()) {
         Iterator<Entry<UUID, Integer>> it = pendingForceShiftHalfHealth.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining > 0) {
               entry.setValue(remaining);
            } else {
               UUID titanUUID = entry.getKey();
               Iterator var5 = server.getWorlds().iterator();

               while (true) {
                  if (var5.hasNext()) {
                     ServerWorld level = (ServerWorld)var5.next();
                     if (!(level.getEntity(titanUUID) instanceof LivingEntity le)) {
                        continue;
                     }

                     le.setHealth(le.getMaxHealth() * 0.5F);
                  }

                  it.remove();
                  break;
               }
            }
         }
      }
   }

   public static void applyAckermanAttributes(ServerPlayerEntity player) {
      EntityAttributeInstance healthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
      if (healthAttr != null && !daot.compat.AttributeModifiers.hasModifier(healthAttr, ACKERMAN_HEALTH_ID)) {
         healthAttr.addTemporaryModifier(daot.compat.AttributeModifiers.create(ACKERMAN_HEALTH_ID, 8.0, Operation.ADDITION));
         player.setHealth(player.getMaxHealth());
      }

      EntityAttributeInstance damageAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
      if (damageAttr != null && !daot.compat.AttributeModifiers.hasModifier(damageAttr, ACKERMAN_DAMAGE_ID)) {
         damageAttr.addTemporaryModifier(daot.compat.AttributeModifiers.create(ACKERMAN_DAMAGE_ID, 0.15, Operation.MULTIPLY_TOTAL));
      }
   }

   public static void applyAtrainAttributes(ServerPlayerEntity player) {
      EntityAttributeInstance fall = player.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.SAFE_FALL_DISTANCE);
      if (fall != null && !daot.compat.AttributeModifiers.hasModifier(fall, ATRAIN_SAFE_FALL_ID)) {
         fall.addTemporaryModifier(daot.compat.AttributeModifiers.create(ATRAIN_SAFE_FALL_ID, 6.0, Operation.ADDITION));
      }
   }

   public static void removeAtrainAttributes(ServerPlayerEntity player) {
      EntityAttributeInstance fall = player.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.SAFE_FALL_DISTANCE);
      if (fall != null) {
         daot.compat.AttributeModifiers.removeModifier(fall, ATRAIN_SAFE_FALL_ID);
      }
   }

   public static void removeAckermanAttributes(ServerPlayerEntity player) {
      EntityAttributeInstance healthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
      if (healthAttr != null) {
         daot.compat.AttributeModifiers.removeModifier(healthAttr, ACKERMAN_HEALTH_ID);
      }

      EntityAttributeInstance damageAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
      if (damageAttr != null) {
         daot.compat.AttributeModifiers.removeModifier(damageAttr, ACKERMAN_DAMAGE_ID);
      }

      if (player.getHealth() > player.getMaxHealth()) {
         player.setHealth(player.getMaxHealth());
      }
   }

   public static void applyOgreShifterAttributes(ServerPlayerEntity player) {
      removeOgreShifterAttributes(player);
   }

   public static void removeOgreShifterAttributes(ServerPlayerEntity player) {
      EntityAttributeInstance healthAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
      if (healthAttr != null) {
         daot.compat.AttributeModifiers.removeModifier(healthAttr, OGRE_SHIFTER_HEALTH_ID);
      }

      if (player.getHealth() > player.getMaxHealth()) {
         player.setHealth(player.getMaxHealth());
      }
   }

   public static void applyTitanBloodlineAttributes(ServerPlayerEntity player) {
      EntityAttributeInstance scaleAttr = player.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.SCALE);
      if (scaleAttr != null && !daot.compat.AttributeModifiers.hasModifier(scaleAttr, TITAN_BLOODLINE_SCALE_ID)) {
         scaleAttr.addTemporaryModifier(daot.compat.AttributeModifiers.create(TITAN_BLOODLINE_SCALE_ID, 0.25, Operation.ADDITION));
      }
   }

   public static void removeTitanBloodlineAttributes(ServerPlayerEntity player) {
      EntityAttributeInstance scaleAttr = player.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.SCALE);
      if (scaleAttr != null) {
         daot.compat.AttributeModifiers.removeModifier(scaleAttr, TITAN_BLOODLINE_SCALE_ID);
      }
   }

   public static void sendTitanBloodlineToPlayer(ServerPlayerEntity player) {
      ServerPlayNetworking.send(player, new TitanBloodlineSyncPayload(player.getCommandTags().contains("titan_bloodline")));
   }

   public static void updateHomelanderAbilities(ServerPlayerEntity player, BloodlineType bloodline) {
      boolean isHomelander = bloodline == BloodlineType.HOMELANDER && !ModEffects.isPowerDisabled(player);
      PlayerAbilities abilities = player.getAbilities();
      if (isHomelander) {
         if (!abilities.allowFlying) {
            abilities.allowFlying = true;
            player.sendAbilitiesUpdate();
         }
      } else if (!player.isCreative() && !player.isSpectator() && (abilities.allowFlying || abilities.flying)) {
         abilities.allowFlying = false;
         abilities.flying = false;
         player.sendAbilitiesUpdate();
      }
   }

   public static void register() {
      PayloadTypeRegistry.playC2S().register(TitanShiftPayload.TYPE, TitanShiftPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(TeaseShiftPayload.TYPE, TeaseShiftPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(TitanAttackPayload.TYPE, TitanAttackPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(TitanSprintPayload.TYPE, TitanSprintPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(TitanArmPayload.TYPE, TitanArmPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(ODMHookUpdatePayload.TYPE, ODMHookUpdatePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(BladeReloadPayload.TYPE, BladeReloadPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(TitanRoarPayload.TYPE, TitanRoarPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(TitanAbilityPayload.TYPE, TitanAbilityPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(JawPounceTargetPayload.TYPE, JawPounceTargetPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(FounderHumanAbilityPayload.TYPE, FounderHumanAbilityPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(StealthTogglePayload.TYPE, StealthTogglePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(GasSyncPayload.TYPE, GasSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(AwakenedPowerPayload.TYPE, AwakenedPowerPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(BladeChargePayload.TYPE, BladeChargePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(HoodTogglePayload.TYPE, HoodTogglePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(ThunderSpearLoadPayload.TYPE, ThunderSpearLoadPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(ThunderSpearFirePayload.TYPE, ThunderSpearFirePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(FlareGunLoadPayload.TYPE, FlareGunLoadPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(FlareGunCyclePayload.TYPE, FlareGunCyclePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(APGFirePayload.TYPE, APGFirePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(APGReloadPayload.TYPE, APGReloadPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(APGAimUpdatePayload.TYPE, APGAimUpdatePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(BladeAOEPayload.TYPE, BladeAOEPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(BladeBlockPayload.TYPE, BladeBlockPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(AnimBroadcastPayload.TYPE, AnimBroadcastPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(HitboxBoneSyncPayload.TYPE, HitboxBoneSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(ColossalHandBoneSyncPayload.TYPE, ColossalHandBoneSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(GrabBoneSyncPayload.TYPE, GrabBoneSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(LegBoneSyncPayload.TYPE, LegBoneSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(SadTitanSprintPayload.TYPE, SadTitanSprintPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(HammerBoneSyncPayload.TYPE, HammerBoneSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(TitanJumpPayload.TYPE, TitanJumpPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(RepelModeTogglePayload.TYPE, RepelModeTogglePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(PushAuraTogglePayload.TYPE, PushAuraTogglePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(PushAuraStatePayload.TYPE, PushAuraStatePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(ReversalTogglePayload.TYPE, ReversalTogglePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(DenialTogglePayload.TYPE, DenialTogglePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(GeassTogglePayload.TYPE, GeassTogglePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(HomelanderFlyTogglePayload.TYPE, HomelanderFlyTogglePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(HomelanderFlyStatePayload.TYPE, HomelanderFlyStatePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(HomelanderGrabIntentPayload.TYPE, HomelanderGrabIntentPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(HomelanderAttackPayload.TYPE, HomelanderAttackPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(HomelanderAttackImpactPayload.TYPE, HomelanderAttackImpactPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(HomelanderNoclipPayload.TYPE, HomelanderNoclipPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(HomelanderFlightInputPayload.TYPE, HomelanderFlightInputPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(HomelanderFlightInputBroadcastPayload.TYPE, HomelanderFlightInputBroadcastPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(HomelanderSonicBoomPayload.TYPE, HomelanderSonicBoomPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(HomelanderLaserPayload.TYPE, HomelanderLaserPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(HomelanderLaserActivePayload.TYPE, HomelanderLaserActivePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(HomelanderLaserDirectionBroadcastPayload.TYPE, HomelanderLaserDirectionBroadcastPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(SoldierboyChargePayload.TYPE, SoldierboyChargePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(SoldierboyChargeStatePayload.TYPE, SoldierboyChargeStatePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(SoldierboyLaserActivePayload.TYPE, SoldierboyLaserActivePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(SoldierboyBlastShakePayload.TYPE, SoldierboyBlastShakePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(ShiftShakePayload.TYPE, ShiftShakePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(TitanImpactShakePayload.TYPE, TitanImpactShakePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(SoldierboyNeckGrabPayload.TYPE, SoldierboyNeckGrabPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(SoldierboyNeckGrabStatePayload.TYPE, SoldierboyNeckGrabStatePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(AtrainSpeedTogglePayload.TYPE, AtrainSpeedTogglePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(TranslucentTogglePayload.TYPE, TranslucentTogglePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(ButcherTentaclePayload.TYPE, ButcherTentaclePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(ButcherTentacleBroadcastPayload.TYPE, ButcherTentacleBroadcastPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(ButcherGrabHoldPayload.TYPE, ButcherGrabHoldPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(ButcherGrabScrollPayload.TYPE, ButcherGrabScrollPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(ButcherGrabStatePayload.TYPE, ButcherGrabStatePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(AtrainSpeedStatePayload.TYPE, AtrainSpeedStatePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(AtrainPlaySpeedSoundPayload.TYPE, AtrainPlaySpeedSoundPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(HomelanderAttackBroadcastPayload.TYPE, HomelanderAttackBroadcastPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(HomelanderGrabIntentBroadcastPayload.TYPE, HomelanderGrabIntentBroadcastPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(HomelanderGrabRotationPayload.TYPE, HomelanderGrabRotationPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(HomelanderGrabRotationSyncPayload.TYPE, HomelanderGrabRotationSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(HomelanderGrabSyncPayload.TYPE, HomelanderGrabSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(MindControlSprintPayload.TYPE, MindControlSprintPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(MindControlActionPayload.TYPE, MindControlActionPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(MindControlRotationPayload.TYPE, MindControlRotationPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(ShifterDodgePayload.TYPE, ShifterDodgePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(APGFireShakePayload.TYPE, APGFireShakePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(StrwsFireShakePayload.TYPE, StrwsFireShakePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(ArmorPotionDrinkPayload.TYPE, ArmorPotionDrinkPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(ShifterDodgeEffectPayload.TYPE, ShifterDodgeEffectPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(MindControlRotationSyncPayload.TYPE, MindControlRotationSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(TitanSpawnPayload.TYPE, TitanSpawnPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(ShiftLightningPayload.TYPE, ShiftLightningPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(PreshiftStartPayload.TYPE, PreshiftStartPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(PlayBitePayload.TYPE, PlayBitePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(PreshiftEffectPayload.TYPE, PreshiftEffectPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(VillagerTransformPayload.TYPE, VillagerTransformPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(VillagerTransformSpawnPayload.TYPE, VillagerTransformSpawnPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(ODMHookSyncPayload.TYPE, ODMHookSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(APGAimSyncPayload.TYPE, APGAimSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(BloodmoonSyncPayload.TYPE, BloodmoonSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(BloodmoonMusicPayload.TYPE, BloodmoonMusicPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(FogSyncPayload.TYPE, FogSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(PoweredVillagerSyncPayload.TYPE, PoweredVillagerSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(EffectPayload.TYPE, EffectPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(StaminaSyncPayload.TYPE, StaminaSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(FounderAnimPayload.TYPE, FounderAnimPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(ZekesGlowPayload.TYPE, ZekesGlowPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(BloodlineSyncPayload.TYPE, BloodlineSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(TitanBloodlineSyncPayload.TYPE, TitanBloodlineSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(TitanDashSyncPayload.TYPE, TitanDashSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(TitanDashAnimPayload.TYPE, TitanDashAnimPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(AwakenedPowerSyncPayload.TYPE, AwakenedPowerSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(TargetGlowPayload.TYPE, TargetGlowPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(TargetModeSyncPayload.TYPE, TargetModeSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(AwakenToggleSyncPayload.TYPE, AwakenToggleSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(HoodSyncPayload.TYPE, HoodSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(ShifterMarkSyncPayload.TYPE, ShifterMarkSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(HomelanderBloodSyncPayload.TYPE, HomelanderBloodSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(HandcuffsSyncPayload.TYPE, HandcuffsSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(AllowODMPayload.TYPE, AllowODMPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(AllowShiftingPayload.TYPE, AllowShiftingPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(AllowThunderSpearsPayload.TYPE, AllowThunderSpearsPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(WeatherAffectsODMPayload.TYPE, WeatherAffectsODMPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(KineticOdmDamagePayload.TYPE, KineticOdmDamagePayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(KineticOdmImpactPayload.TYPE, KineticOdmImpactPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(RealisticResourceUsePayload.TYPE, RealisticResourceUsePayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(GrassODMPayload.TYPE, GrassODMPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(ChargedODMAttacksPayload.TYPE, ChargedODMAttacksPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(BladeBlockBrokenPayload.TYPE, BladeBlockBrokenPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(BladeAnimSyncPayload.TYPE, BladeAnimSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(ODMJamPayload.TYPE, ODMJamPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(ReversalSyncPayload.TYPE, ReversalSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(DenialSyncPayload.TYPE, DenialSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(FreezeVignetteSyncPayload.TYPE, FreezeVignetteSyncPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(GeassControlPayload.TYPE, GeassControlPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(MindControlInputPayload.TYPE, MindControlInputPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(StrwsAimPayload.TYPE, StrwsAimPayload.STREAM_CODEC);
      PayloadTypeRegistry.playC2S().register(StrwsControlPayload.TYPE, StrwsControlPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(StrwsControlPayload.TYPE, StrwsControlPayload.STREAM_CODEC);
      PayloadTypeRegistry.playS2C().register(StrwsAimSyncPayload.TYPE, StrwsAimSyncPayload.STREAM_CODEC);
      ServerPlayNetworking.registerGlobalReceiver(StrwsAimPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         BlockPos pos = payload.controller();
         float yaw = payload.yaw();
         float pitch = payload.pitch();
         context.server().execute(() -> {
            if (pos.equals(StrwsAimServerState.get(player.getUuid()))) {
               if (player.getWorld().getBlockEntity(pos) instanceof StrwsBlockEntity be) {
                  be.setAim(yaw, pitch);
                  StrwsAimSyncPayload var8 = new StrwsAimSyncPayload(pos, yaw, pitch);

                  for (ServerPlayerEntity nearby : PlayerLookup.tracking(be)) {
                     if (nearby != player) {
                        ServerPlayNetworking.send(nearby, var8);
                     }
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(StrwsControlPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BlockPos controlled = StrwsAimServerState.get(player.getUuid());
            if (controlled != null) {
               if (player.getWorld().getBlockEntity(controlled) instanceof StrwsBlockEntity be) {
                  be.setController(null);
               }

               StrwsAimServerState.clear(player.getUuid());
               ServerPlayNetworking.send(player, new StrwsControlPayload(controlled, false));
            }
         });
      });
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> StrwsMultiblock.handleDisconnect(handler.player));
      ServerPlayNetworking.registerGlobalReceiver(MindControlRotationPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity controller = context.player();
         if (GeassManager.isMindController(controller.getUuid())) {
            controller.setYaw(payload.yaw());
            controller.setPitch(payload.pitch());
            UUID targetUUID = GeassManager.getMindControlTargetUUID();
            if (targetUUID != null) {
               ServerPlayerEntity target = context.server().getPlayerManager().getPlayer(targetUUID);
               if (target != null) {
                  try {
                     ServerPlayNetworking.send(target, new MindControlRotationSyncPayload(payload.yaw(), payload.pitch()));
                  } catch (Exception var6) {
                  }
               }
            }
         }
      });
      ServerPlayNetworking.registerGlobalReceiver(
         MindControlActionPayload.TYPE,
         (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server()
               .execute(
                  () -> GeassManager.handleControllerAction(
                     player, payload.action(), payload.entityId(), payload.blockPosLong(), payload.direction(), payload.hand()
                  )
               );
         }
      );
      ServerPlayNetworking.registerGlobalReceiver(
         MindControlSprintPayload.TYPE,
         (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server()
               .execute(
                  () -> GeassManager.setControllerExtraInput(
                     player, payload.forward(), payload.strafe(), payload.jumping(), payload.sprinting(), payload.shifting(), payload.inventoryOpen()
                  )
               );
         }
      );
      ServerPlayNetworking.registerGlobalReceiver(GeassTogglePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            String playerName = player.getGameProfile().getName();
            if ("Speakor".equals(playerName) || "Inrupt".equals(playerName)) {
               GeassManager.toggle(player);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(PushAuraTogglePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            String name = player.getGameProfile().getName();
            if ("Speakor".equals(name) || "Inrupt".equals(name)) {
               PushAuraTracker.toggleActive(player);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(RepelModeTogglePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            if (PushAuraTracker.isActive(player.getUuid())) {
               PushAuraTracker.toggleAttract(player);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(HomelanderGrabIntentPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.HOMELANDER) {
               if (!ModEffects.isPowerDisabled(player)) {
                  HomelanderGrabServerHandler.setIntent(player, payload.wantsGrab());
                  HomelanderGrabIntentBroadcastPayload broadcast = new HomelanderGrabIntentBroadcastPayload(player.getUuid(), payload.wantsGrab());

                  for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                     if (!p.getUuid().equals(player.getUuid())) {
                        ServerPlayNetworking.send(p, broadcast);
                     }
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(HomelanderAttackPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.HOMELANDER) {
               if (!ModEffects.isPowerDisabled(player)) {
                  if (HomelanderFlightServerHandler.isFlying(player.getUuid())) {
                     HomelanderGrabServerHandler.scheduleAttack(player, payload.attackType());
                     HomelanderAttackBroadcastPayload broadcast = new HomelanderAttackBroadcastPayload(player.getUuid(), payload.attackType());

                     for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                        if (!p.getUuid().equals(player.getUuid())) {
                           ServerPlayNetworking.send(p, broadcast);
                        }
                     }
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(HomelanderAttackImpactPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.HOMELANDER) {
               if (!ModEffects.isPowerDisabled(player)) {
                  if (HomelanderFlightServerHandler.isFlying(player.getUuid())) {
                     HomelanderGrabServerHandler.triggerKeyframeImpact(player, payload.attackType());
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(HomelanderFlightInputPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.HOMELANDER) {
               if (!ModEffects.isPowerDisabled(player)) {
                  if (HomelanderFlightServerHandler.isFlying(player.getUuid())) {
                     HomelanderFlightServerHandler.cacheInputBits(player.getUuid(), payload.bits());
                     HomelanderFlightInputBroadcastPayload broadcast = new HomelanderFlightInputBroadcastPayload(player.getUuid(), payload.bits());

                     for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                        if (!p.getUuid().equals(player.getUuid())) {
                           ServerPlayNetworking.send(p, broadcast);
                        }
                     }
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(HomelanderGrabRotationPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity grabber = context.player();
         int victimId = HomelanderGrabServerHandler.getGrabbedEntityId(grabber.getUuid());
         if (victimId != -1) {
            for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
               if (p.getId() == victimId) {
                  try {
                     ServerPlayNetworking.send(p, new HomelanderGrabRotationSyncPayload(payload.yaw(), payload.pitch()));
                  } catch (Exception var7) {
                  }
                  break;
               }
            }
         }
      });
      ServerPlayNetworking.registerGlobalReceiver(HomelanderSonicBoomPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.HOMELANDER) {
               if (!ModEffects.isPowerDisabled(player)) {
                  if (HomelanderFlightServerHandler.isFlying(player.getUuid())) {
                     if (player.getWorld() instanceof ServerWorld level) {
                        HomelanderBloodTracker.onSonicBoom(player);
                        double var30 = payload.x();
                        double y = payload.y();
                        double z = payload.z();
                        level.playSound(player, var30, y, z, ModSounds.BOOM, SoundCategory.PLAYERS, 3.0F, 1.0F);
                        Vec3d forward = new Vec3d(payload.dirX(), payload.dirY(), payload.dirZ());
                        if (forward.lengthSquared() < 1.0E-4) {
                           forward = new Vec3d(1.0, 0.0, 0.0);
                        } else {
                           forward = forward.normalize();
                        }

                        Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
                        Vec3d u = forward.crossProduct(worldUp);
                        if (u.lengthSquared() < 1.0E-4) {
                           u = forward.crossProduct(new Vec3d(1.0, 0.0, 0.0));
                        }

                        u = u.normalize();
                        Vec3d v = forward.crossProduct(u).normalize();
                        DustParticleEffect whiteDust = new DustParticleEffect(new Vector3f(1.0F, 1.0F, 1.0F), 1.4F);
                        int count = 32;
                        double[][] points = new double[70][3];
                        int idx = 0;

                        for (int i = 0; i < 32; i++) {
                           double angle = (Math.PI * 2) * i / 32.0;
                           double cs = Math.cos(angle);
                           double sn = Math.sin(angle);
                           double r1 = 2.2;
                           points[idx][0] = var30 + (u.x * cs + v.x * sn) * r1;
                           points[idx][1] = y + (u.y * cs + v.y * sn) * r1;
                           points[idx][2] = z + (u.z * cs + v.z * sn) * r1;
                           idx++;
                           double r2 = 1.4;
                           points[idx][0] = var30 + (u.x * cs + v.x * sn) * r2;
                           points[idx][1] = y + (u.y * cs + v.y * sn) * r2;
                           points[idx][2] = z + (u.z * cs + v.z * sn) * r2;
                           idx++;
                        }

                        for (int i = 0; i < 6; i++) {
                           points[idx][0] = var30 + (level.random.nextDouble() - 0.5) * 0.6;
                           points[idx][1] = y + (level.random.nextDouble() - 0.5) * 0.6;
                           points[idx][2] = z + (level.random.nextDouble() - 0.5) * 0.6;
                           idx++;
                        }

                        for (ServerPlayerEntity p : level.getPlayers()) {
                           if (!p.getUuid().equals(player.getUuid()) && !(p.squaredDistanceTo(var30, y, z) > 65536.0)) {
                              for (double[] pt : points) {
                                 level.spawnParticles(p, whiteDust, true, pt[0], pt[1], pt[2], 1, 0.0, 0.0, 0.0, 0.0);
                              }
                           }
                        }
                     }
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(HomelanderLaserPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.HOMELANDER) {
               if (!ModEffects.isPowerDisabled(player)) {
                  if (player.getWorld() instanceof ServerWorld level) {
                     Vec3d dir = new Vec3d(payload.dirX(), payload.dirY(), payload.dirZ());
                     if (!(dir.lengthSquared() < 1.0E-4)) {
                        dir = dir.normalize();
                        UUID firerId = player.getUuid();
                        long now = level.getTime();
                        lastLaserPayloadTick.put(firerId, now);
                        if (activeLaserFirers.add(firerId)) {
                           broadcastLaserActive(context.server(), firerId, true);
                        }

                        HomelanderLaserDirectionBroadcastPayload dirBroadcast = new HomelanderLaserDirectionBroadcastPayload(firerId, dir.x, dir.y, dir.z);

                        for (ServerPlayerEntity recipient : context.server().getPlayerManager().getPlayerList()) {
                           if (!recipient.getUuid().equals(firerId)) {
                              ServerPlayNetworking.send(recipient, dirBroadcast);
                           }
                        }

                        Vec3d prevDir = lastLaserDir.get(firerId);
                        if (prevDir == null) {
                           prevDir = dir;
                        }

                        lastLaserDir.put(firerId, dir);
                        Set<UUID> hitEntitiesThisTick = new HashSet<>();
                        Vec3d eye = player.getEyePos();

                        for (int i = 1; i <= 5; i++) {
                           float t = i / 5.0F;
                           Vec3d sampleDir = prevDir.multiply(1.0 - t).add(dir.multiply(t));
                           if (!(sampleDir.lengthSquared() < 1.0E-4)) {
                              sampleDir = sampleDir.normalize();
                              Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
                              Vec3d sampleRight = sampleDir.crossProduct(worldUp);
                              if (sampleRight.lengthSquared() < 1.0E-4) {
                                 sampleRight = sampleDir.crossProduct(new Vec3d(1.0, 0.0, 0.0));
                              }

                              sampleRight = sampleRight.normalize();
                              Vec3d leftEye = eye.add(sampleRight.multiply(-0.15));
                              Vec3d rightEye = eye.add(sampleRight.multiply(0.15));
                              fireLaserBeam(level, player, leftEye, sampleDir, hitEntitiesThisTick);
                              fireLaserBeam(level, player, rightEye, sampleDir, hitEntitiesThisTick);
                           }
                        }
                     }
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(
         SoldierboyChargePayload.TYPE,
         (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server()
               .execute(
                  () -> {
                     BloodlineData blData = BloodlineData.get(context.server());
                     if (blData.getBloodline(player.getUuid()) == BloodlineType.SOLDIERBOY) {
                        if (!ModEffects.isPowerDisabled(player)) {
                           if (player.getWorld() instanceof ServerWorld level) {
                              UUID var10 = player.getUuid();
                              if (payload.action() == 0) {
                                 SoldierboyChargeStatePayload broadcast = new SoldierboyChargeStatePayload(var10, payload.ability(), true);

                                 for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                                    if (!p.getUuid().equals(var10)) {
                                       ServerPlayNetworking.send(p, broadcast);
                                    }
                                 }

                                 Vec3d startOrigin = player.getPos().add(0.0, player.getHeight() * 0.5, 0.0);
                                 level.playSound(
                                    null,
                                    startOrigin.x,
                                    startOrigin.y,
                                    startOrigin.z,
                                    SoundEvents.ENTITY_WARDEN_SONIC_CHARGE,
                                    SoundCategory.PLAYERS,
                                    10.0F,
                                    1.0F
                                 );
                                 if (!player.isCreative() && !player.isSpectator() && !player.getAbilities().allowFlying) {
                                    player.getAbilities().allowFlying = true;
                                    player.getAbilities().flying = true;
                                    player.sendAbilitiesUpdate();
                                 }
                              } else if (payload.action() == 2) {
                                 if (payload.ability() == 1) {
                                    lastChestLaserTick.put(var10, level.getTime());
                                    if (activeChestLaserFirers.add(var10)) {
                                       SoldierboyChargeStatePayload chargeOff = new SoldierboyChargeStatePayload(var10, payload.ability(), false);
                                       SoldierboyLaserActivePayload laserOn = new SoldierboyLaserActivePayload(var10, true);

                                       for (ServerPlayerEntity px : context.server().getPlayerManager().getPlayerList()) {
                                          ServerPlayNetworking.send(px, chargeOff);
                                          ServerPlayNetworking.send(px, laserOn);
                                       }

                                       Vec3d origin = player.getPos().add(0.0, player.getHeight() * 0.5, 0.0);
                                       level.playSound(
                                          null, origin.x, origin.y, origin.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 10.0F, 0.9F
                                       );
                                    }

                                    Vec3d lookFire = new Vec3d(payload.dirX(), payload.dirY(), payload.dirZ());
                                    if (!(lookFire.lengthSquared() < 1.0E-4)) {
                                       fireChestLaserTick(level, player, lookFire);
                                    }
                                 }
                              } else {
                                 SoldierboyChargeStatePayload off = new SoldierboyChargeStatePayload(var10, payload.ability(), false);

                                 for (ServerPlayerEntity px : context.server().getPlayerManager().getPlayerList()) {
                                    if (!px.getUuid().equals(var10)) {
                                       ServerPlayNetworking.send(px, off);
                                    }
                                 }

                                 if (activeChestLaserFirers.remove(var10)) {
                                    lastChestLaserTick.remove(var10);
                                    SoldierboyLaserActivePayload laserOff = new SoldierboyLaserActivePayload(var10, false);

                                    for (ServerPlayerEntity pxx : context.server().getPlayerManager().getPlayerList()) {
                                       ServerPlayNetworking.send(pxx, laserOff);
                                    }
                                 }

                                 if (!player.isCreative() && !player.isSpectator() && player.getAbilities().allowFlying) {
                                    player.getAbilities().allowFlying = false;
                                    player.getAbilities().flying = false;
                                    player.sendAbilitiesUpdate();
                                 }

                                 float progress = payload.chargeProgress();
                                 if (!(progress < 0.2F)) {
                                    if (payload.ability() == 0) {
                                       fireChestBlast(level, player, progress);
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               );
         }
      );
      ServerPlayNetworking.registerGlobalReceiver(SoldierboyNeckGrabPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.SOLDIERBOY) {
               switch (payload.action()) {
                  case 0:
                     SoldierboyNeckGrabServerHandler.handleXPress(player);
                     break;
                  case 2:
                     SoldierboyNeckGrabServerHandler.punchGrabbed(player);
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(HomelanderNoclipPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.HOMELANDER) {
               if (!ModEffects.isPowerDisabled(player)) {
                  if (!HomelanderFlightServerHandler.isFlying(player.getUuid())) {
                     if (player.noClip) {
                        player.noClip = false;
                     }
                  } else {
                     player.noClip = payload.wantsNoclip();
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(ButcherTentaclePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.BUTCHER) {
               if (!ModEffects.isPowerDisabled(player)) {
                  ButcherTentacleServerHandler.fire(player);
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(ButcherGrabHoldPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.BUTCHER) {
               if (ModEffects.isPowerDisabled(player)) {
                  ButcherGrabHandler.stopGrab(player);
               } else {
                  if (payload.active()) {
                     ButcherGrabHandler.startGrab(player);
                  } else {
                     ButcherGrabHandler.stopGrab(player);
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(ButcherGrabScrollPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.BUTCHER) {
               if (!ModEffects.isPowerDisabled(player)) {
                  ButcherGrabHandler.scroll(player, payload.notches());
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(TranslucentTogglePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.TRANSLUCENT) {
               if (!ModEffects.isPowerDisabled(player)) {
                  TranslucentTracker.setToggled(player, payload.active());
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(AtrainSpeedTogglePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.ATRAIN) {
               if (!ModEffects.isPowerDisabled(player)) {
                  AtrainSpeedTracker.setToggled(player.getUuid(), payload.active());
                  AtrainSpeedStatePayload broadcast = new AtrainSpeedStatePayload(player.getUuid(), payload.active());

                  for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                     ServerPlayNetworking.send(p, broadcast);
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(AtrainPlaySpeedSoundPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.ATRAIN) {
               if (!ModEffects.isPowerDisabled(player)) {
                  if (AtrainSpeedTracker.isToggledOn(player.getUuid())) {
                     AtrainSpeedTracker.playSpeedSound(player);
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(HomelanderFlyTogglePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            BloodlineData blData = BloodlineData.get(context.server());
            if (blData.getBloodline(player.getUuid()) == BloodlineType.HOMELANDER) {
               if (!ModEffects.isPowerDisabled(player)) {
                  HomelanderFlightServerHandler.setFlying(player, payload.flying());
                  HomelanderFlyStatePayload broadcast = new HomelanderFlyStatePayload(player.getUuid(), payload.flying(), payload.flying());

                  for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                     ServerPlayNetworking.send(p, broadcast);
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(TitanShiftPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         ServerWorld level = player.getServerWorld();
         context.server().execute(() -> {
            if (!DannysAot.isShiftingAllowed(level) && !player.isCreative() && !player.hasPermissionLevel(2)) {
               player.sendMessage(Text.literal("Shifting is Disabled").styled(style -> style.withColor(Formatting.RED)), true);
            } else {
               handleTitanShift(player, level);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(TeaseShiftPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         ServerWorld level = player.getServerWorld();
         context.server().execute(() -> {
            if (!DannysAot.isShiftingAllowed(level) && !player.isCreative() && !player.hasPermissionLevel(2)) {
               player.sendMessage(Text.literal("Shifting is Disabled").styled(style -> style.withColor(Formatting.RED)), true);
            } else {
               handleTeaseShift(player, level);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(TitanAttackPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> handleTitanAttack(player));
      });
      ServerPlayNetworking.registerGlobalReceiver(TitanJumpPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            Entity wv = player.getVehicle();
            if (wv == null || !StrwsRestraintTracker.isFullyRestrained(wv.getUuid())) {
               if (player.getVehicle() instanceof WarhammerTitanEntity warhammerTitan) {
                  UUID shifterUUID = warhammerTitan.getShifterUUID();
                  if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                     warhammerTitan.triggerJump(player);
                  }
               } else if (player.getVehicle() instanceof ArmoredTitanEntity armoredTitan) {
                  UUID shifterUUID = armoredTitan.getShifterUUID();
                  if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                     armoredTitan.triggerJump(player);
                  }
               } else if (player.getVehicle() instanceof AttackTitanEntity attackTitan) {
                  UUID shifterUUID = attackTitan.getShifterUUID();
                  if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                     attackTitan.triggerJump(player);
                  }
               } else if (player.getVehicle() instanceof FemaleTitanEntity femaleTitan) {
                  UUID shifterUUID = femaleTitan.getShifterUUID();
                  if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                     femaleTitan.triggerJump(player);
                  }
               } else if (player.getVehicle() instanceof BeastTitanEntity beastTitan) {
                  UUID shifterUUID = beastTitan.getShifterUUID();
                  if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                     beastTitan.triggerJump(player);
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(JawPounceTargetPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         int entityId = payload.entityId();
         context.server().execute(() -> {
            if (player.getVehicle() instanceof TestShifterTitanEntity jaw) {
               jaw.setPendingPounceTarget(entityId);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(TitanAbilityPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         int abilityNumber = payload.abilityNumber();
         context.server().execute(() -> handleTitanAbility(player, abilityNumber));
      });
      ServerPlayNetworking.registerGlobalReceiver(FounderHumanAbilityPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         int abilityNumber = payload.abilityNumber();
         context.server().execute(() -> {
            if (player.getCommandTags().contains("founder")) {
               handleFounderAbility(player, abilityNumber);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(ShifterDodgePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         int direction = payload.direction();
         context.server().execute(() -> ShifterDodgeManager.tryStart(player, direction));
      });
      ServerPlayNetworking.registerGlobalReceiver(TitanSprintPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         boolean sprinting = payload.sprinting();
         context.server().execute(() -> handleTitanSprint(player, sprinting));
      });
      ServerPlayNetworking.registerGlobalReceiver(TitanArmPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         boolean armed = payload.armed();
         context.server().execute(() -> handleTitanArm(player, armed));
      });
      ServerPlayNetworking.registerGlobalReceiver(
         StealthTogglePayload.TYPE,
         (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server()
               .execute(
                  () -> {
                     boolean isShifter = player.getCommandTags().contains("attack")
                        || player.getCommandTags().contains("colossal")
                        || player.getCommandTags().contains("armored")
                        || player.getCommandTags().contains("beast")
                        || player.getCommandTags().contains("female")
                        || player.getCommandTags().contains("warhammer")
                        || player.getCommandTags().contains("founder")
                        || player.getCommandTags().contains("triple_t")
                        || player.getCommandTags().contains("ogre_shifter")
                        || player.getCommandTags().contains("jaw")
                        || player.getCommandTags().contains("cart_shifter");
                     if (!isShifter) {
                        player.sendMessage(Text.literal("No shifter powers"), true);
                     } else {
                        if (player.getCommandTags().contains("titan_stealth")) {
                           player.removeScoreboardTag("titan_stealth");
                           player.sendMessage(Text.literal("Titan Stealth Disabled").styled(style -> style.withColor(Formatting.RED)), true);
                        } else {
                           player.addCommandTag("titan_stealth");
                           player.sendMessage(Text.literal("Titan Stealth Enabled").styled(style -> style.withColor(Formatting.GREEN)), true);
                        }
                     }
                  }
               );
         }
      );
      ServerPlayNetworking.registerGlobalReceiver(HoodTogglePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            ItemStack headStack = player.getEquippedStack(EquipmentSlot.HEAD);
            if (headStack.getItem() instanceof CloakItem) {
               HoodTracker.toggleHood(player);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(ODMHookUpdatePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> handleODMHookUpdate(player, payload));
      });
      ServerPlayNetworking.registerGlobalReceiver(KineticOdmImpactPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         float reportedSpeed = payload.speed();
         context.server().execute(() -> handleKineticOdmImpact(player, reportedSpeed));
      });
      ServerPlayNetworking.registerGlobalReceiver(BladeReloadPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> handleBladeReload(player));
      });
      ServerPlayNetworking.registerGlobalReceiver(ThunderSpearLoadPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> handleThunderSpearLoad(player));
      });
      ServerPlayNetworking.registerGlobalReceiver(ThunderSpearFirePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         int handOrdinal = payload.hand();
         float clientXRot = payload.xRot();
         float clientYRot = payload.yRot();
         context.server().execute(() -> handleThunderSpearFire(player, handOrdinal == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND, clientXRot, clientYRot));
      });
      ServerPlayNetworking.registerGlobalReceiver(FlareGunLoadPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> handleFlareGunLoad(player));
      });
      ServerPlayNetworking.registerGlobalReceiver(FlareGunCyclePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> handleFlareGunCycle(player));
      });
      ServerPlayNetworking.registerGlobalReceiver(APGFirePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         float clientXRot = payload.xRot();
         float clientYRot = payload.yRot();
         int handMask = payload.handMask();
         context.server().execute(() -> handleAPGFire(player, clientXRot, clientYRot, handMask));
      });
      ServerPlayNetworking.registerGlobalReceiver(APGReloadPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> handleAPGReload(player));
      });
      ServerPlayNetworking.registerGlobalReceiver(APGAimUpdatePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         boolean main = payload.mainAiming();
         boolean off = payload.offAiming();
         context.server().execute(() -> {
            APGAimSyncPayload sync = new APGAimSyncPayload(player.getId(), main, off);

            for (ServerPlayerEntity nearby : PlayerLookup.tracking(player)) {
               if (nearby != player) {
                  ServerPlayNetworking.send(nearby, sync);
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(TitanRoarPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> handleTitanRoar(player));
      });
      ServerPlayNetworking.registerGlobalReceiver(GasSyncPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            ItemStack leggings = player.getEquippedStack(EquipmentSlot.LEGS);
            if (DannysAot.isODMGear(leggings.getItem())) {
               int gas = Math.max(0, Math.min(DannysAot.getMaxGasForGear(leggings), payload.gas()));
               DannysAot.setGasOnGear(leggings, gas);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(AwakenedPowerPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         context.server().execute(() -> {
            if (player.getCommandTags().contains("titan_bloodline")) {
               TitanDashTracker.activate(player);
            } else if (player.getCommandTags().contains("ogre_shifter")) {
               OgreHealAbility.tryHeal(player, player.getVehicle() instanceof OgreShifterTitanEntity ogre ? ogre : null);
            } else {
               BloodlineData bloodlineData = BloodlineData.get(player.getServerWorld());
               if (bloodlineData.getBloodline(player.getUuid()) != BloodlineType.ACKERMAN) {
                  player.sendMessage(Text.literal("Only Ackerman's can use this ability").styled(style -> style.withColor(Formatting.RED)), true);
               } else if (!AwakenedPowerTracker.isActive(player.getUuid())) {
                  AwakenedPowerTracker.activate(player);
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(BladeChargePayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         float multiplier = Math.min(Math.max(payload.multiplier(), 1.0F), 1.5F);
         context.server().execute(() -> {
            EntityAttributeInstance damageAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            if (damageAttr != null) {
               daot.compat.AttributeModifiers.removeModifier(damageAttr, BLADE_CHARGE_ID);
               if (multiplier > 1.001F) {
                  damageAttr.addTemporaryModifier(daot.compat.AttributeModifiers.create(BLADE_CHARGE_ID, multiplier - 1.0F, Operation.MULTIPLY_TOTAL));
               }

               bladeChargeExpiry.put(player.getUuid(), context.server().getTicks() + 3L);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(BladeAOEPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         int attackType = payload.attackType();
         int animAction = payload.animAction();
         context.server().execute(() -> {
            ItemStack mainHand = player.getMainHandStack();
            ItemStack offHand = player.getOffHandStack();
            boolean mainBlade = mainHand.getItem() == DannysAot.BLADE && BladeItem.getBladeState(mainHand) != BladeItem.BladeState.EMPTY;
            boolean offBlade = offHand.getItem() == DannysAot.BLADE && BladeItem.getBladeState(offHand) != BladeItem.BladeState.EMPTY;
            if (mainBlade || offBlade) {
               BladeAttackTracker.startAttack(player, attackType, payload.chargeTimeTicks(), payload.playerSpeed());
               broadcastBladeAnim(player, animAction);
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(BladeBlockPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         boolean blocking = payload.blocking();
         context.server().execute(() -> {
            BladeBlockTracker.setBlocking(player, blocking);
            broadcastBladeAnim(player, blocking ? 0 : 1);
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(AnimBroadcastPayload.TYPE, (payload, context) -> {
         ServerPlayerEntity player = context.player();
         int action = payload.action();
         context.server().execute(() -> broadcastBladeAnim(player, action));
      });
      ServerPlayNetworking.registerGlobalReceiver(HitboxBoneSyncPayload.TYPE, (payload, context) -> {
         int entityId = payload.entityId();
         double napeX = payload.napeX();
         double napeY = payload.napeY();
         double napeZ = payload.napeZ();
         double eyeX = payload.eyeX();
         double eyeY = payload.eyeY();
         double eyeZ = payload.eyeZ();
         context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            ServerWorld level = player.getServerWorld();
            Entity entity = level.getEntityById(entityId);
            if (entity != null) {
               if (!(player.squaredDistanceTo(entity) > 6400.0)) {
                  MobEntity nape = null;
                  MobEntity eye = null;
                  if (entity instanceof TitanEntity titan) {
                     nape = titan.getNapeEntity();
                     eye = titan.getEyeEntity();
                  } else if (entity instanceof AttackTitanEntity titan) {
                     nape = titan.getNapeEntity();
                     eye = titan.getEyeEntity();
                  } else if (entity instanceof ArmoredTitanEntity titan) {
                     nape = titan.getNapeEntity();
                     eye = titan.getEyeEntity();
                  } else if (entity instanceof ColossalTitanEntity titan) {
                     nape = titan.getNapeEntity();
                     eye = titan.getEyeEntity();
                  } else if (entity instanceof FemaleTitanEntity titan) {
                     nape = titan.getNapeEntity();
                     eye = titan.getEyeEntity();
                  } else if (entity instanceof BeastTitanEntity titan) {
                     nape = titan.getNapeEntity();
                     eye = titan.getEyeEntity();
                  } else if (entity instanceof SmallTitanEntity titan) {
                     nape = titan.getNapeEntity();
                     eye = titan.getEyeEntity();
                  } else if (entity instanceof SmallTitan2Entity titan) {
                     nape = titan.getNapeEntity();
                     eye = titan.getEyeEntity();
                  } else if (entity instanceof FritzTitanEntity titan) {
                     nape = titan.getNapeEntity();
                     eye = titan.getEyeEntity();
                  } else if (entity instanceof WarhammerTitanEntity titan) {
                     nape = titan.getNapeEntity();
                     eye = titan.getEyeEntity();
                  } else if (entity instanceof ConnieFatherEntity titan) {
                     nape = titan.getNapeEntity();
                     eye = titan.getEyeEntity();
                  } else if (entity instanceof OgreTitanEntity titan) {
                     nape = titan.getNapeEntity();
                  } else if (entity instanceof CrawlerTitanEntity titan) {
                     nape = titan.getNapeEntity();
                     eye = titan.getEyeEntity();
                  } else if (entity instanceof TitanDummyEntity dummy) {
                     nape = dummy.getNapeEntity();
                     eye = dummy.getEyeEntity();
                  }

                  if (nape != null && nape.isAlive()) {
                     if (nape instanceof TitanNapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     } else if (nape instanceof AttackTitanNapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     } else if (nape instanceof ArmoredTitanNapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     } else if (nape instanceof ColossalTitanNapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     } else if (nape instanceof FemaleTitanNapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     } else if (nape instanceof BeastTitanNapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     } else if (nape instanceof SmallTitanNapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     } else if (nape instanceof SmallTitan2NapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     } else if (nape instanceof FritzTitanNapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     } else if (nape instanceof WarhammerTitanNapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     } else if (nape instanceof ConnieFatherNapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     } else if (nape instanceof OgreTitanNapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     } else if (nape instanceof CrawlerTitanNapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     } else if (nape instanceof TitanDummyNapeEntity e) {
                        e.setSyncedPosition(napeX, napeY, napeZ);
                     }
                  }

                  if (eye != null && eye.isAlive()) {
                     if (eye instanceof TitanEyeEntity e) {
                        e.setSyncedPosition(eyeX, eyeY, eyeZ);
                     } else if (eye instanceof AttackTitanEyeEntity e) {
                        e.setSyncedPosition(eyeX, eyeY, eyeZ);
                     } else if (eye instanceof ArmoredTitanEyeEntity e) {
                        e.setSyncedPosition(eyeX, eyeY, eyeZ);
                     } else if (eye instanceof ColossalTitanEyeEntity e) {
                        e.setSyncedPosition(eyeX, eyeY, eyeZ);
                     } else if (eye instanceof FemaleTitanEyeEntity e) {
                        e.setSyncedPosition(eyeX, eyeY, eyeZ);
                     } else if (eye instanceof BeastTitanEyeEntity e) {
                        e.setSyncedPosition(eyeX, eyeY, eyeZ);
                     } else if (eye instanceof SmallTitanEyeEntity e) {
                        e.setSyncedPosition(eyeX, eyeY, eyeZ);
                     } else if (eye instanceof SmallTitan2EyeEntity e) {
                        e.setSyncedPosition(eyeX, eyeY, eyeZ);
                     } else if (eye instanceof FritzTitanEyeEntity e) {
                        e.setSyncedPosition(eyeX, eyeY, eyeZ);
                     } else if (eye instanceof WarhammerTitanEyeEntity e) {
                        e.setSyncedPosition(eyeX, eyeY, eyeZ);
                     } else if (eye instanceof ConnieFatherEyeEntity e) {
                        e.setSyncedPosition(eyeX, eyeY, eyeZ);
                     } else if (eye instanceof CrawlerTitanEyeEntity e) {
                        e.setSyncedPosition(eyeX, eyeY, eyeZ);
                     } else if (eye instanceof TitanDummyEyeEntity e) {
                        e.setSyncedPosition(eyeX, eyeY, eyeZ);
                     }
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(ColossalHandBoneSyncPayload.TYPE, (payload, context) -> {
         int entityId = payload.entityId();
         double handX = payload.handX();
         double handY = payload.handY();
         double handZ = payload.handZ();
         context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            ServerWorld level = player.getServerWorld();
            Entity entity = level.getEntityById(entityId);
            if (entity != null) {
               if (!(player.squaredDistanceTo(entity) > 6400.0)) {
                  if (entity instanceof ColossalTitanEntity titan) {
                     ColossalTitanHandEntity hand = titan.getHandEntity();
                     if (hand != null && hand.isAlive()) {
                        hand.setSyncedPosition(handX, handY, handZ);
                     }
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(GrabBoneSyncPayload.TYPE, (payload, context) -> {
         int entityId = payload.entityId();
         double gx = payload.grabX();
         double gy = payload.grabY();
         double gz = payload.grabZ();
         context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            ServerWorld level = player.getServerWorld();
            Entity entity = level.getEntityById(entityId);
            if (entity != null) {
               if (!(player.squaredDistanceTo(entity) > 6400.0)) {
                  if (entity instanceof BeastTitanEntity beastTitan) {
                     beastTitan.setGrabSyncedPosition(gx, gy, gz, context.server().getTicks());
                     BeastTitanGrabEntity grabEntity = beastTitan.getGrabHitboxEntity();
                     if (grabEntity != null) {
                        grabEntity.setSyncedPosition(gx, gy, gz);
                     }
                  } else if (entity instanceof FemaleTitanEntity femaleTitan) {
                     femaleTitan.setGrabSyncedPosition(gx, gy, gz, context.server().getTicks());
                     FemaleTitanGrabEntity grabEntity = femaleTitan.getGrabHitboxEntity();
                     if (grabEntity != null) {
                        grabEntity.setSyncedPosition(gx, gy, gz);
                     }
                  } else if (entity instanceof AttackTitanEntity attackTitan) {
                     attackTitan.setGrabSyncedPosition(gx, gy, gz, context.server().getTicks());
                     AttackTitanGrabEntity grabEntity = attackTitan.getGrabHitboxEntity();
                     if (grabEntity != null) {
                        grabEntity.setSyncedPosition(gx, gy, gz);
                     }
                  } else if (entity instanceof ArmoredTitanEntity armoredTitan) {
                     ArmoredTitanGrabEntity grabEntity = armoredTitan.getGrabHitboxEntity();
                     if (grabEntity != null) {
                        grabEntity.setSyncedPosition(gx, gy, gz);
                     }
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(LegBoneSyncPayload.TYPE, (payload, context) -> {
         int entityId = payload.entityId();
         double lx = payload.leftX();
         double ly = payload.leftY();
         double lz = payload.leftZ();
         double rx = payload.rightX();
         double ry = payload.rightY();
         double rz = payload.rightZ();
         context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            ServerWorld level = player.getServerWorld();
            Entity entity = level.getEntityById(entityId);
            if (entity instanceof ArmoredTitanEntity armoredTitan) {
               if (!(player.squaredDistanceTo(entity) > 6400.0)) {
                  ArmoredTitanLegEntity leftLeg = armoredTitan.getLeftLegEntity();
                  if (leftLeg != null && leftLeg.isAlive()) {
                     leftLeg.setSyncedPosition(lx, ly, lz);
                  }

                  ArmoredTitanLegEntity rightLeg = armoredTitan.getRightLegEntity();
                  if (rightLeg != null && rightLeg.isAlive()) {
                     rightLeg.setSyncedPosition(rx, ry, rz);
                  }
               }
            }
         });
      });
      ServerPlayNetworking.registerGlobalReceiver(SadTitanSprintPayload.TYPE, (payload, context) -> context.server().execute(() -> {
         ServerPlayerEntity player = context.player();
         if (player.getVehicle() instanceof SadTitanEntity sadTitan && sadTitan.isRideable) {
            sadTitan.setRideableSprinting(payload.sprinting());
         }
      }));
      ServerPlayNetworking.registerGlobalReceiver(HammerBoneSyncPayload.TYPE, (payload, context) -> {
         int entityId = payload.entityId();
         double hx = payload.hammerX();
         double hy = payload.hammerY();
         double hz = payload.hammerZ();
         context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            ServerWorld level = player.getServerWorld();
            Entity entity = level.getEntityById(entityId);
            if (entity != null) {
               if (!(player.squaredDistanceTo(entity) > 6400.0)) {
                  if (entity instanceof WarhammerTitanEntity titan) {
                     titan.setHammerBonePosition(hx, hy, hz, context.server().getTicks());
                  }
               }
            }
         });
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         ShifterDodgeManager.serverTick(server);
         PowerAuthority.sweep(server);
         tickPendingBites(server);
         tickPendingShifts(server);
         tickAscendingPlayers(server);
         tickPendingForceShiftHalfHealth(server);
         tickColossalShiftFreeze(server);
         tickShifterRegeneration(server);
         tickShifterStamina(server);
         tickStealthInvisibility(server);
         tickBladeChargeExpiry(server);
         tickPendingCommands(server);
         tickStopHeartbeat(server);
         tickLaserFirerTimeouts(server);
         tickChestLaserFirerTimeouts(server);
         tickPowerRegen(server);
         tickPowerDisableSync(server);
         AtrainSpeedTracker.tick(server);
         ButcherTentacleServerHandler.tick(server);
         ButcherGrabHandler.tick(server);
         BladeBlockTracker.tickSlowdowns(server);
         BladeAttackTracker.tick(server);
         TitanPowerHelper.tickPathsReturns(server);
         if (server.getTicks() % 20 == 0) {
            tickZekesGlassesGlow(server);
         }

         if (server.getTicks() % 20 == 0) {
            syncPlayerPowerTags(server);
         }

         tickTargetAbility(server);
         tickFounderTarget(server);
         tickFounderGuards(server);
         tickBloodmoonMusicRotation(server);
         tickAwakenStamina(server);
         tickBeastGrabHighlight(server);
      });
      ServerPlayConnectionEvents.JOIN
         .register(
            (Join)(handler, sender, server) -> {
               ServerPlayerEntity player = handler.getPlayer();
               sendConfigToPlayer(player);
               sendAllowODMToPlayer(player);
               sendAllowShiftingToPlayer(player);
               sendAllowThunderSpearsToPlayer(player);
               sendWeatherAffectsODMToPlayer(player);
               sendKineticOdmDamageToPlayer(player);
               sendRealisticResourceUseToPlayer(player);
               sendGrassODMToPlayer(player);
               sendChargedODMAttacksToPlayer(player);
               ServerPlayNetworking.send(player, new BloodmoonSyncPayload(BloodmoonState.isActive()));
               if (BloodmoonState.isActive()) {
                  int idx = BloodmoonState.getCurrentTrackIndex(server.getTicks());
                  ServerPlayNetworking.send(player, new BloodmoonMusicPayload(idx));
               }

               sendFogState(player);
               DannysAot.LOGGER.info("Sent config sync to player {}", player.getName().getString());
               sendHomelanderFlightStateTo(player);
               float maxStam = getMaxStaminaForPlayer(player);
               playerMaxStamina.put(player.getUuid(), maxStam);
               if (!playerStamina.containsKey(player.getUuid())) {
                  playerStamina.put(player.getUuid(), maxStam);
               }

               boolean isShifterOnJoin = player.getCommandTags().contains("attack")
                  || player.getCommandTags().contains("colossal")
                  || player.getCommandTags().contains("armored")
                  || player.getCommandTags().contains("beast")
                  || player.getCommandTags().contains("female")
                  || player.getCommandTags().contains("warhammer")
                  || player.getCommandTags().contains("founder")
                  || player.getCommandTags().contains("triple_t")
                  || player.getCommandTags().contains("ogre_shifter")
                  || player.getCommandTags().contains("jaw")
                  || player.getCommandTags().contains("cart_shifter");
               if (isShifterOnJoin) {
                  float currentStamina = playerStamina.getOrDefault(player.getUuid(), maxStam);
                  boolean hasBeast = player.getCommandTags().contains("beast");
                  boolean hasFounding = player.getCommandTags().contains("founder");
                  ServerPlayNetworking.send(player, new StaminaSyncPayload(currentStamina, maxStam, hasBeast, hasFounding));
               }

               BloodlineData bloodlineData = BloodlineData.get(server);
               if (!bloodlineData.hasBloodline(player.getUuid())) {
                  ModConfig cfg = ModConfig.get();
                  float totalWeight = cfg.eldianWeight + cfg.marleyanWeight + cfg.ackermanWeight + cfg.royalWeight;
                  float roll = player.getRandom().nextFloat() * totalWeight;
                  BloodlineType assigned;
                  if (roll < cfg.royalWeight) {
                     assigned = BloodlineType.ROYAL;
                  } else if (roll < cfg.royalWeight + cfg.ackermanWeight) {
                     assigned = BloodlineType.ACKERMAN;
                  } else if (roll < cfg.royalWeight + cfg.ackermanWeight + cfg.marleyanWeight) {
                     assigned = BloodlineType.MARLEYAN;
                  } else {
                     assigned = BloodlineType.ELDIAN;
                  }

                  bloodlineData.setBloodline(player.getUuid(), assigned, GrantProvenance.ofRoll(player.getServer()));
                  player.sendMessage(ModCommands.buildBloodlineMessage(assigned));
                  DannysAot.LOGGER.info("Assigned {} bloodline to {}", assigned.getCommandName(), player.getName().getString());
                  ServerPlayNetworking.send(player, new BloodlineSyncPayload(player.getUuid(), assigned.ordinal()));
                  broadcastBloodlineToAll(player, assigned.ordinal());
                  syncAllBloodlinesToPlayer(player, server, bloodlineData);
                  if (assigned == BloodlineType.ACKERMAN) {
                     applyAckermanAttributes(player);
                     player.removeScoreboardTag("dannys-aot:awakened_power");
                     AwakenedPowerTracker.ensureTracked(player.getUuid());
                     ServerPlayNetworking.send(player, new AwakenedPowerSyncPayload(AwakenedPowerTracker.getCharge(player.getUuid()), 300.0F, false));
                  }

                  if (assigned == BloodlineType.ATRAIN) {
                     applyAtrainAttributes(player);
                  }

                  updateHomelanderAbilities(player, assigned);
               } else {
                  BloodlineType existing = bloodlineData.getBloodline(player.getUuid());
                  ServerPlayNetworking.send(player, new BloodlineSyncPayload(player.getUuid(), existing.ordinal()));
                  broadcastBloodlineToAll(player, existing.ordinal());
                  syncAllBloodlinesToPlayer(player, server, bloodlineData);
                  if (existing == BloodlineType.ACKERMAN) {
                     applyAckermanAttributes(player);
                     player.removeScoreboardTag("dannys-aot:awakened_power");
                     AwakenedPowerTracker.ensureTracked(player.getUuid());
                     ServerPlayNetworking.send(player, new AwakenedPowerSyncPayload(AwakenedPowerTracker.getCharge(player.getUuid()), 300.0F, false));
                  }

                  if (existing == BloodlineType.ATRAIN) {
                     applyAtrainAttributes(player);
                  }

                  updateHomelanderAbilities(player, existing);
               }

               if (player.getCommandTags().contains("ogre_shifter")) {
                  applyOgreShifterAttributes(player);
               }

               if (player.getCommandTags().contains("titan_bloodline")) {
                  applyTitanBloodlineAttributes(player);
                  TitanDashTracker.ensureAndSync(player);
               }

               sendTitanBloodlineToPlayer(player);
               server.execute(() -> {
                  if (player.getWorld() instanceof ServerWorld serverLevel) {
                     TitanPowerHelper.syncAllPoweredVillagersToPlayer(player, serverLevel);
                  }
               });
               HoodTracker.syncToPlayer(player);
               ShifterMarkTracker.syncToPlayer(player);
               HomelanderBloodTracker.syncToPlayer(player);
            }
         );
      ServerEntityEvents.ENTITY_LOAD.register((Load)(entity, level) -> {
         if (entity instanceof VillagerEntity villager) {
            TitanPowerData data = TitanPowerData.get(level);
            TitanPowerType power = data.getPower(villager.getUuid());
            if (power != null) {
               boolean playerHasPower = anyOnlinePlayerHasPower(level.getServer(), power);
               if (playerHasPower) {
                  data.removePower(villager.getUuid());
                  DannysAot.LOGGER.info("Removed {} power from loading villager (player has it)", power.getDisplayName());
               } else {
                  TitanPowerHelper.broadcastPowerAdd(level, villager.getId(), power);
               }
            }
         }
      });
      ServerPlayerEvents.AFTER_RESPAWN.register((AfterRespawn)(oldPlayer, newPlayer, alive) -> {
         BladeBlockTracker.onPlayerDisconnect(oldPlayer.getUuid());
         if (!alive) {
            VillagerTransformTracker.removeInjectedPlayer(newPlayer);
         }

         BloodlineData bloodlineData = BloodlineData.get(newPlayer.getServerWorld());
         BloodlineType respawnBl = bloodlineData.getBloodline(newPlayer.getUuid());
         if (newPlayer.getCommandTags().contains("ogre_shifter")) {
            applyOgreShifterAttributes(newPlayer);
         }

         if (newPlayer.getCommandTags().contains("titan_bloodline")) {
            applyTitanBloodlineAttributes(newPlayer);
            TitanDashTracker.ensureAndSync(newPlayer);
         }

         sendTitanBloodlineToPlayer(newPlayer);
         if (respawnBl == BloodlineType.ATRAIN) {
            applyAtrainAttributes(newPlayer);
         }

         if (respawnBl == BloodlineType.ACKERMAN) {
            applyAckermanAttributes(newPlayer);
            newPlayer.removeScoreboardTag("dannys-aot:awakened_power");
            AwakenedPowerTracker.ensureTracked(newPlayer.getUuid());
            ServerPlayNetworking.send(newPlayer, new AwakenedPowerSyncPayload(AwakenedPowerTracker.getCharge(newPlayer.getUuid()), 300.0F, false));
         }

         broadcastAwakenToggle(newPlayer, false);
      });
      daot.compat.DamageEvents.AFTER_DAMAGE.register((AfterDamage)(entity, source, baseDamage, damageTaken, blocked) -> {
         if (entity instanceof ServerPlayerEntity founder) {
            if (founder.getCommandTags().contains("founder")) {
               List<UUID> guards = founderGuards.get(founder.getUuid());
               if (guards != null && !guards.isEmpty()) {
                  if (source.getAttacker() instanceof LivingEntity attacker) {
                     if (attacker != founder && !(attacker instanceof OgreTitanEntity)) {
                        for (UUID id : guards) {
                           OgreTitanEntity ogre = findOgre(founder.server, id);
                           if (ogre != null && ogre.isAlive()) {
                              ogre.setGuardRetaliationTarget(attacker, 200);
                           }
                        }
                     }
                  }
               }
            }
         }
      });
      ServerLivingEntityEvents.AFTER_DEATH.register((AfterDeath)(entity, damageSource) -> {
         if (entity instanceof ServerPlayerEntity deadPlayer && HandcuffsTracker.isCuffed(deadPlayer.getUuid())) {
            HandcuffsTracker.uncuffEntity(deadPlayer, null);
         }

         if (entity instanceof ServerPlayerEntity deadPlayer && awakenActive.contains(deadPlayer.getUuid())) {
            releaseAllTitansOnDeath(deadPlayer);
            awakenActive.remove(deadPlayer.getUuid());
         }

         if (entity.getWorld() instanceof ServerWorld level) {
            boolean hasAttackTag = entity.getCommandTags().contains("attack");
            boolean hasColossalTag = entity.getCommandTags().contains("colossal");
            boolean hasArmoredTag = entity.getCommandTags().contains("armored");
            boolean hasBeastTag = entity.getCommandTags().contains("beast");
            boolean hasFemaleTag = entity.getCommandTags().contains("female");
            boolean wasBeingEaten = entity.getCommandTags().contains("dannys-aot:being_grabbed");
            Entity dmgEntity = damageSource.getAttacker();
            if (wasBeingEaten) {
               entity.removeScoreboardTag("dannys-aot:being_grabbed");
            }

            if (wasBeingEaten && TitanPowerHelper.isPureTitan(dmgEntity)) {
               TitanPowerHelper.handleEatingCompletion((HostileEntity)dmgEntity, entity, level);
               return;
            }

            if (wasBeingEaten) {
               Entity vehicle = entity.getVehicle();
               if (TitanPowerHelper.isPureTitan(vehicle)) {
                  TitanPowerHelper.handleEatingCompletion((HostileEntity)vehicle, entity, level);
               }

               return;
            }

            if (entity instanceof ServerPlayerEntity player) {
               boolean hasAttack = player.getCommandTags().contains("attack");
               boolean hasColossal = player.getCommandTags().contains("colossal");
               boolean hasArmored = player.getCommandTags().contains("armored");
               boolean hasBeast = player.getCommandTags().contains("beast");
               boolean hasFemale = player.getCommandTags().contains("female");
               boolean hasWarhammer = player.getCommandTags().contains("warhammer");
               if ((hasAttack || hasColossal || hasArmored || hasBeast || hasFemale || hasWarhammer) && !DannysAot.isFairTitanPowerLossEnabled(level)) {
                  TitanPowerHelper.handlePlayerPowerLoss(player, level);
               }
            }

            if (entity instanceof VillagerEntity villager) {
               TitanPowerData data = TitanPowerData.get(level);
               if (data.hasPower(villager.getUuid()) && !TitanPowerHelper.tryShifterInheritFromVillager(dmgEntity, villager, level)) {
                  TitanPowerHelper.handleVillagerPowerLoss(villager, level);
               }
            }
         }
      });
      VillagerTransformTracker.initialize();
      AwakenedPowerTracker.initialize();
      TitanDashTracker.initialize();
      TitanTossManager.initialize();
      UseItemCallback.EVENT
         .register(
            (UseItemCallback)(player, world, hand) -> isRidingTitanNotInEject(player)
               ? TypedActionResult.fail(ItemStack.EMPTY)
               : TypedActionResult.pass(ItemStack.EMPTY)
         );
      UseBlockCallback.EVENT
         .register((UseBlockCallback)(player, world, hand, hitResult) -> isRidingTitanNotInEject(player) ? ActionResult.FAIL : ActionResult.PASS);
      AttackBlockCallback.EVENT
         .register((AttackBlockCallback)(player, world, hand, pos, direction) -> isRidingTitanNotInEject(player) ? ActionResult.FAIL : ActionResult.PASS);
      DannysAot.LOGGER.info("Registered titan shift networking");
   }

   public static void broadcastBloodmoon(MinecraftServer server, boolean active) {
      BloodmoonSyncPayload payload = new BloodmoonSyncPayload(active);

      for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(p, payload);
      }
   }

   private static FogSyncPayload buildFogPayload(MinecraftServer server) {
      BreachManager data = BreachManager.get(server);
      return new FogSyncPayload(FogEventState.isActive(), data.breachedWallsArray(), data.breachedDistrictsArray());
   }

   public static void broadcastFogState(MinecraftServer server) {
      FogSyncPayload payload = buildFogPayload(server);

      for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(p, payload);
      }
   }

   public static void sendFogState(ServerPlayerEntity player) {
      ServerPlayNetworking.send(player, buildFogPayload(player.getServerWorld().getServer()));
   }

   public static void broadcastBloodmoonMusic(MinecraftServer server, int trackIndex) {
      BloodmoonMusicPayload payload = new BloodmoonMusicPayload(trackIndex);

      for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(p, payload);
      }
   }

   private static void tickBloodmoonMusicRotation(MinecraftServer server) {
      if (!BloodmoonState.isActive()) {
         lastBroadcastBloodmoonTrack = -1;
      } else {
         int idx = BloodmoonState.getCurrentTrackIndex(server.getTicks());
         if (idx != lastBroadcastBloodmoonTrack) {
            lastBroadcastBloodmoonTrack = idx;
            broadcastBloodmoonMusic(server, idx);
         }
      }
   }

   public static void sendConfigToPlayer(ServerPlayerEntity player) {
      ModConfig config = ModConfig.get();
      ConfigSyncPayload payload = new ConfigSyncPayload(
         config.dualHookEaseTime,
         config.gasTickInterval,
         config.gasConsumptionNormal,
         config.gasConsumptionBoost,
         config.basePullSpeed,
         config.dualHookPullMultiplier,
         config.dualHookBoostPullMultiplier,
         config.orbitPullMultiplier,
         config.baseOrbitSpeed,
         config.dualHookOrbitMultiplier,
         config.upwardLift,
         config.boostPullMultiplier,
         config.boostOrbitMultiplier,
         config.boostRampRate,
         config.boostDecayRate,
         config.maxHookDistance,
         config.momentumPreserveTime,
         config.flightSoundVelocityThreshold
      );
      ServerPlayNetworking.send(player, payload);
   }

   public static void sendHomelanderFlightStateTo(ServerPlayerEntity recipient) {
      UUID recipId = recipient.getUuid();

      for (UUID flyer : HomelanderFlightServerHandler.currentlyFlying()) {
         if (!flyer.equals(recipId)) {
            ServerPlayNetworking.send(recipient, new HomelanderFlyStatePayload(flyer, true, false));
            byte bits = HomelanderFlightServerHandler.getCachedInputBits(flyer);
            if (bits != 0) {
               ServerPlayNetworking.send(recipient, new HomelanderFlightInputBroadcastPayload(flyer, bits));
            }

            if (HomelanderGrabServerHandler.hasGrabIntent(flyer)) {
               ServerPlayNetworking.send(recipient, new HomelanderGrabIntentBroadcastPayload(flyer, true));
            }
         }
      }
   }

   public static void sendHomelanderFlightStateForPlayerTo(ServerPlayerEntity flyer, ServerPlayerEntity recipient) {
      UUID flyerId = flyer.getUuid();
      if (HomelanderFlightServerHandler.isFlying(flyerId)) {
         ServerPlayNetworking.send(recipient, new HomelanderFlyStatePayload(flyerId, true, false));
         byte bits = HomelanderFlightServerHandler.getCachedInputBits(flyerId);
         if (bits != 0) {
            ServerPlayNetworking.send(recipient, new HomelanderFlightInputBroadcastPayload(flyerId, bits));
         }

         if (HomelanderGrabServerHandler.hasGrabIntent(flyerId)) {
            ServerPlayNetworking.send(recipient, new HomelanderGrabIntentBroadcastPayload(flyerId, true));
         }
      }
   }

   public static void sendAllowODMToPlayer(ServerPlayerEntity player) {
      boolean allowed = DannysAot.isODMAllowed(player.getWorld());
      ServerPlayNetworking.send(player, new AllowODMPayload(allowed));
   }

   public static void broadcastAllowODM(MinecraftServer server) {
      boolean allowed = server.getGameRules().getBoolean(DannysAot.RULE_ALLOW_ODM);
      AllowODMPayload payload = new AllowODMPayload(allowed);

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(player, payload);
      }
   }

   public static void sendGrassODMToPlayer(ServerPlayerEntity player) {
      boolean allowed = player.getWorld().getGameRules().getBoolean(DannysAot.RULE_GRASS_ODM);
      ServerPlayNetworking.send(player, new GrassODMPayload(allowed));
   }

   public static void broadcastGrassODM(MinecraftServer server) {
      boolean allowed = server.getGameRules().getBoolean(DannysAot.RULE_GRASS_ODM);
      GrassODMPayload payload = new GrassODMPayload(allowed);

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(player, payload);
      }
   }

   public static void sendChargedODMAttacksToPlayer(ServerPlayerEntity player) {
      boolean enabled = player.getWorld().getGameRules().getBoolean(DannysAot.RULE_CHARGED_ODM_ATTACKS);
      ServerPlayNetworking.send(player, new ChargedODMAttacksPayload(enabled));
   }

   public static void broadcastChargedODMAttacks(MinecraftServer server) {
      boolean enabled = server.getGameRules().getBoolean(DannysAot.RULE_CHARGED_ODM_ATTACKS);
      ChargedODMAttacksPayload payload = new ChargedODMAttacksPayload(enabled);

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(player, payload);
      }
   }

   public static void sendAllowShiftingToPlayer(ServerPlayerEntity player) {
      boolean allowed = DannysAot.isShiftingAllowed(player.getWorld());
      ServerPlayNetworking.send(player, new AllowShiftingPayload(allowed));
   }

   public static void broadcastAllowShifting(MinecraftServer server) {
      boolean allowed = server.getGameRules().getBoolean(DannysAot.RULE_ALLOW_SHIFTING);
      AllowShiftingPayload payload = new AllowShiftingPayload(allowed);

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(player, payload);
      }
   }

   public static void sendAllowThunderSpearsToPlayer(ServerPlayerEntity player) {
      boolean allowed = DannysAot.areThunderSpearsAllowed(player.getWorld());
      ServerPlayNetworking.send(player, new AllowThunderSpearsPayload(allowed));
   }

   public static void broadcastAllowThunderSpears(MinecraftServer server) {
      boolean allowed = server.getGameRules().getBoolean(DannysAot.RULE_ALLOW_THUNDER_SPEARS);
      AllowThunderSpearsPayload payload = new AllowThunderSpearsPayload(allowed);

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(player, payload);
      }
   }

   public static void sendWeatherAffectsODMToPlayer(ServerPlayerEntity player) {
      boolean enabled = player.getWorld().getGameRules().getBoolean(DannysAot.RULE_WEATHER_AFFECTS_ODM);
      ServerPlayNetworking.send(player, new WeatherAffectsODMPayload(enabled));
   }

   public static void broadcastWeatherAffectsODM(MinecraftServer server) {
      boolean enabled = server.getGameRules().getBoolean(DannysAot.RULE_WEATHER_AFFECTS_ODM);
      WeatherAffectsODMPayload payload = new WeatherAffectsODMPayload(enabled);

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(player, payload);
      }
   }

   public static void sendKineticOdmDamageToPlayer(ServerPlayerEntity player) {
      boolean enabled = player.getWorld().getGameRules().getBoolean(DannysAot.RULE_KINETIC_ODM_DAMAGE);
      ServerPlayNetworking.send(player, new KineticOdmDamagePayload(enabled));
   }

   public static void broadcastKineticOdmDamage(MinecraftServer server) {
      boolean enabled = server.getGameRules().getBoolean(DannysAot.RULE_KINETIC_ODM_DAMAGE);
      KineticOdmDamagePayload payload = new KineticOdmDamagePayload(enabled);

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(player, payload);
      }
   }

   public static void sendRealisticResourceUseToPlayer(ServerPlayerEntity player) {
      boolean enabled = player.getWorld().getGameRules().getBoolean(DannysAot.RULE_REALISTIC_RESOURCE_USE);
      ServerPlayNetworking.send(player, new RealisticResourceUsePayload(enabled));
      if (enabled) {
         dropRealisticResourceExcess(player);
      }
   }

   public static void broadcastRealisticResourceUse(MinecraftServer server) {
      boolean enabled = server.getGameRules().getBoolean(DannysAot.RULE_REALISTIC_RESOURCE_USE);
      RealisticResourceUsePayload payload = new RealisticResourceUsePayload(enabled);

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(player, payload);
         if (enabled) {
            dropRealisticResourceExcess(player);
         }
      }
   }

   public static void dropRealisticResourceExcess(ServerPlayerEntity player) {
      if (!player.isCreative() && !player.isSpectator()) {
         PlayerInventory inv = player.getInventory();
         int bladeTotal = RealisticResourceCaps.countBlades(inv);
         if (bladeTotal > 4) {
            int excess = bladeTotal - 4;

            for (int i = inv.size() - 1; i >= 0 && excess > 0; i--) {
               ItemStack s = inv.getStack(i);
               if (!s.isEmpty() && s.getItem() instanceof BladeComponentItem) {
                  int drop = Math.min(excess, s.getCount());
                  ItemStack dropStack = s.copyWithCount(drop);
                  s.decrement(drop);
                  player.dropItem(dropStack, false, true);
                  excess -= drop;
               }
            }

            for (int ix = inv.size() - 1; ix >= 0 && excess > 0; ix--) {
               ItemStack s = inv.getStack(ix);
               if (!s.isEmpty() && s.getItem() instanceof BladeItem && BladeItem.getBladeState(s) != BladeItem.BladeState.EMPTY) {
                  ItemStack dropStack = s.copy();
                  inv.setStack(ix, ItemStack.EMPTY);
                  player.dropItem(dropStack, false, true);
                  excess--;
               }
            }
         }

         int canisterTotal = RealisticResourceCaps.countCanisters(inv);
         if (canisterTotal > 2) {
            int excess = canisterTotal - 2;

            for (int ixx = inv.size() - 1; ixx >= 0 && excess > 0; ixx--) {
               ItemStack s = inv.getStack(ixx);
               if (!s.isEmpty() && s.getItem() instanceof GasCanisterItem) {
                  int drop = Math.min(excess, s.getCount());
                  ItemStack dropStack = s.copyWithCount(drop);
                  s.decrement(drop);
                  player.dropItem(dropStack, false, true);
                  excess -= drop;
               }
            }
         }
      }
   }

   private static void handleKineticOdmImpact(ServerPlayerEntity player, float reportedSpeed) {
      if (player != null && !player.isRemoved()) {
         if (player.getWorld().getGameRules().getBoolean(DannysAot.RULE_KINETIC_ODM_DAMAGE)) {
            if (!player.isCreative() && !player.isSpectator()) {
               if (!player.isInvulnerable()) {
                  if (!player.getCommandTags().contains("ogre_shifter")) {
                     Item legsItem = player.getEquippedStack(EquipmentSlot.LEGS).getItem();
                     if (legsItem == DannysAot.ODM_GEAR || legsItem == DannysAot.ODM_APG) {
                        float speed = Math.max(0.0F, Math.min(reportedSpeed, 5.0F));
                        if (!(speed <= 1.2F)) {
                           float damage = Math.min((speed - 1.2F) * 7.0F, 20.0F);
                           if (!(damage <= 0.0F)) {
                              DamageSource source = player.getDamageSources().flyIntoWall();
                              player.damage(source, damage);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void handleTitanAttack(ServerPlayerEntity player) {
      Entity wireVehicle = player.getVehicle();
      if (wireVehicle == null || !StrwsRestraintTracker.isFullyRestrained(wireVehicle.getUuid())) {
         if (player.getVehicle() instanceof AttackTitanEntity attackTitan) {
            UUID shifterUUID = attackTitan.getShifterUUID();
            if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
               attackTitan.triggerAttack();
            }
         } else if (player.getVehicle() instanceof ArmoredTitanEntity armoredTitan) {
            UUID shifterUUID = armoredTitan.getShifterUUID();
            if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
               armoredTitan.triggerAttack();
            }
         } else if (player.getVehicle() instanceof FemaleTitanEntity femaleTitan) {
            UUID shifterUUID = femaleTitan.getShifterUUID();
            if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
               femaleTitan.triggerAttack();
            }
         } else if (player.getVehicle() instanceof ColossalTitanEntity colossalTitan) {
            UUID shifterUUID = colossalTitan.getShifterUUID();
            if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
               colossalTitan.triggerAttack();
            }
         } else if (player.getVehicle() instanceof BeastTitanEntity beastTitan) {
            UUID shifterUUID = beastTitan.getShifterUUID();
            if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
               beastTitan.triggerAttack();
            }
         } else if (player.getVehicle() instanceof WarhammerTitanEntity warhammerTitan) {
            UUID shifterUUID = warhammerTitan.getShifterUUID();
            if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
               warhammerTitan.triggerAttack();
            }
         }
      }
   }

   private static void handleTitanSprint(ServerPlayerEntity player, boolean sprinting) {
      if (player.getVehicle() instanceof AttackTitanEntity attackTitan) {
         UUID shifterUUID = attackTitan.getShifterUUID();
         if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
            attackTitan.setSprinting(sprinting);
         }
      } else if (player.getVehicle() instanceof ArmoredTitanEntity armoredTitan) {
         UUID shifterUUID = armoredTitan.getShifterUUID();
         if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
            if (sprinting && !armoredTitan.isLegsShattered()) {
               player.sendMessage(Text.literal("Shatter your leg armor to run!").formatted(Formatting.RED), true);
            }

            armoredTitan.setSprinting(sprinting);
         }
      } else if (player.getVehicle() instanceof FemaleTitanEntity femaleTitan) {
         UUID shifterUUID = femaleTitan.getShifterUUID();
         if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
            femaleTitan.setSprinting(sprinting);
         }
      } else if (player.getVehicle() instanceof BeastTitanEntity beastTitan) {
         UUID shifterUUID = beastTitan.getShifterUUID();
         if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
            beastTitan.setSprinting(sprinting);
         }
      } else if (player.getVehicle() instanceof WarhammerTitanEntity warhammerTitan) {
         UUID shifterUUID = warhammerTitan.getShifterUUID();
         if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
            warhammerTitan.setSprinting(sprinting);
         }
      }
   }

   private static void handleTitanArm(ServerPlayerEntity player, boolean armed) {
      Entity wireVehicle = player.getVehicle();
      if (wireVehicle == null || !StrwsRestraintTracker.isFullyRestrained(wireVehicle.getUuid())) {
         if (player.getVehicle() instanceof TestShifterTitanEntity testTitan) {
            UUID shifterUUID = testTitan.getShifterUUID();
            if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
               testTitan.setLatchRequested(armed);
            }
         } else {
            if (player.getVehicle() instanceof AttackTitanEntity attackTitan) {
               UUID shifterUUID = attackTitan.getShifterUUID();
               if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                  attackTitan.setArmed(armed);
               }
            } else if (player.getVehicle() instanceof ArmoredTitanEntity armoredTitan) {
               UUID shifterUUID = armoredTitan.getShifterUUID();
               if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                  armoredTitan.setArmed(armed);
               }
            } else if (player.getVehicle() instanceof FemaleTitanEntity femaleTitan) {
               UUID shifterUUID = femaleTitan.getShifterUUID();
               if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                  femaleTitan.setArmed(armed);
               }
            } else if (player.getVehicle() instanceof BeastTitanEntity beastTitan) {
               UUID shifterUUID = beastTitan.getShifterUUID();
               if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                  beastTitan.setThrowTurning(armed);
                  if (!armed && beastTitan.getHighlightedEntityId() != -1) {
                     beastTitan.setHighlightedEntityId(-1);
                     ServerPlayNetworking.send(player, new TargetGlowPayload(new int[0]));
                  }
               }
            } else if (player.getVehicle() instanceof WarhammerTitanEntity warhammerTitan) {
               UUID shifterUUID = warhammerTitan.getShifterUUID();
               if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                  warhammerTitan.setArmed(armed);
               }
            }
         }
      }
   }

   private static void handleTitanAbility(ServerPlayerEntity player, int abilityNumber) {
      boolean needsHardening = (abilityNumber == 6 || abilityNumber == 9)
         && (player.getVehicle() instanceof AttackTitanEntity || player.getVehicle() instanceof FemaleTitanEntity);
      if (needsHardening && !player.getCommandTags().contains("has_hardening")) {
         player.sendMessage(Text.literal("This ability requires consumption of the Armor Potion to use.").formatted(Formatting.RED), false);
      } else {
         Entity wireVehicle = player.getVehicle();
         boolean allowWhileWired = wireVehicle instanceof AttackTitanEntity && abilityNumber == 3
            || wireVehicle instanceof FemaleTitanEntity && (abilityNumber == 5 || abilityNumber == 3);
         if (wireVehicle != null && !allowWhileWired && StrwsRestraintTracker.getWireCount(wireVehicle.getUuid()) >= 1) {
            player.sendMessage(Text.literal("Restrained by wires - can't use abilities!").formatted(Formatting.RED), true);
         } else {
            if (player.getVehicle() instanceof AttackTitanEntity attackTitan) {
               UUID shifterUUID = attackTitan.getShifterUUID();
               if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                  attackTitan.triggerAbility(abilityNumber);
               }
            } else if (player.getVehicle() instanceof ArmoredTitanEntity armoredTitan) {
               UUID shifterUUID = armoredTitan.getShifterUUID();
               if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                  armoredTitan.triggerAbility(abilityNumber);
               }
            } else if (player.getVehicle() instanceof FemaleTitanEntity femaleTitan) {
               UUID shifterUUID = femaleTitan.getShifterUUID();
               if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                  femaleTitan.triggerAbility(abilityNumber);
               }
            } else if (player.getVehicle() instanceof ColossalTitanEntity colossalTitan) {
               UUID shifterUUID = colossalTitan.getShifterUUID();
               if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                  if (abilityNumber == 1) {
                     colossalTitan.triggerSteamAbility();
                  } else if (abilityNumber == 2) {
                     colossalTitan.triggerKickAbility();
                  } else if (abilityNumber == 3) {
                     colossalTitan.triggerArmDragAbility();
                  } else if (abilityNumber == 4) {
                     colossalTitan.triggerInfernalHeatAbility();
                  }
               }
            } else {
               if (player.getVehicle() instanceof BeastTitanEntity beastTitan) {
                  UUID shifterUUID = beastTitan.getShifterUUID();
                  if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                     if (abilityNumber == 1) {
                        int highlightedId = beastTitan.getHighlightedEntityId();
                        if (highlightedId != -1 && beastTitan.getRockThrowPhase() == 0) {
                           Entity target = player.getServerWorld().getEntityById(highlightedId);
                           if (target != null && target.isAlive() && target instanceof LivingEntity) {
                              beastTitan.grabEntity(target);
                              beastTitan.setHighlightedEntityId(-1);
                              ServerPlayNetworking.send(player, new TargetGlowPayload(new int[0]));
                              return;
                           }
                        }

                        beastTitan.triggerRockGrab();
                        return;
                     }

                     if (abilityNumber == 2) {
                        beastTitan.triggerFireRockGrab();
                        return;
                     }

                     if (abilityNumber == 3) {
                        beastTitan.triggerBigRockGrab();
                        return;
                     }

                     if (abilityNumber == 4) {
                        beastTitan.triggerBeastRoarAbility();
                        return;
                     }
                  }

                  dispatchBeastRoyalAbility(player, abilityNumber);
                  return;
               }

               if (player.getVehicle() instanceof WarhammerTitanEntity warhammerTitan) {
                  UUID shifterUUID = warhammerTitan.getShifterUUID();
                  if (shifterUUID != null && player.getUuid().equals(shifterUUID)) {
                     warhammerTitan.triggerAbility(abilityNumber);
                  }
               } else if (player.getCommandTags().contains("beast")) {
                  dispatchBeastRoyalAbility(player, abilityNumber);
               } else if (player.getCommandTags().contains("founder")) {
                  handleFounderAbility(player, abilityNumber);
               }
            }
         }
      }
   }

   private static void handleFounderAbility(ServerPlayerEntity player, int abilityNumber) {
      if (abilityNumber == 2) {
         handleFounderRegroup(player);
      } else if (abilityNumber == 3) {
         handleFounderStop(player);
      } else if (abilityNumber == 4) {
         handleFounderToggleScope(player);
      } else if (abilityNumber == 5) {
         handleFounderSummon(player);
      } else if (abilityNumber == 6) {
         handleFounderGuards(player);
      } else if (abilityNumber == 7) {
         handleFounderMount(player);
      } else if (abilityNumber == 9) {
         handleFounderDespawn(player);
      } else if (abilityNumber == 1) {
         Entity looked = findEntityInAimCone(player, 200.0, 0.85, e -> e instanceof LivingEntity && !isTitanHitboxPart(e));
         if (looked == null) {
            player.sendMessage(Text.literal("No target in sight.").formatted(Formatting.GRAY), true);
         } else {
            String ownerName = player.getName().getString();
            String targetName = looked.getName().getString();
            String ownerTag = "dannysaot_roar_owner:" + ownerName;
            UUID targetUUID = looked.getUuid();
            double radius = 200.0;
            Box box = new Box(
               player.getX() - radius, player.getY() - radius, player.getZ() - radius, player.getX() + radius, player.getY() + radius, player.getZ() + radius
            );
            int commanded = 0;

            for (HostileEntity mob : player.getServerWorld().getNonSpectatingEntities(HostileEntity.class, box)) {
               if (isCommandTarget(player, mob, ownerTag)) {
                  if (mob.getUuid().equals(targetUUID)) {
                     mob.removeScoreboardTag(ownerTag);
                  } else {
                     for (String tag : new ArrayList<>(mob.getCommandTags())) {
                        if (tag.startsWith("dannysaot_roar_owner:") && !tag.equals(ownerTag)) {
                           mob.removeScoreboardTag(tag);
                        }
                     }

                     if (!mob.getCommandTags().contains(ownerTag)) {
                        mob.addCommandTag(ownerTag);
                     }

                     cancelTitanEating(mob);
                     mob.removeScoreboardTag("dannysaot_commanded_stop");
                     mob.removeScoreboardTag("dannysaot_regroup_no_collision");
                     if (VillagerTransformTracker.isRegrouping(mob)) {
                        VillagerTransformTracker.clearRegroup(mob);
                     }

                     commanded++;
                  }
               }
            }

            VillagerTransformTracker.setActiveTarget(ownerName, targetUUID, targetName);
            founderTargets.put(player.getUuid(), new ModNetworking.FounderTarget(targetUUID, ownerName));
            founderStopActive.remove(player.getUuid());
            player.sendMessage(Text.literal(commanded + " titan(s) now hunting " + targetName).formatted(Formatting.GOLD, Formatting.BOLD), true);

            for (ServerPlayerEntity nearby : PlayerLookup.tracking(player)) {
               ServerPlayNetworking.send(nearby, new FounderAnimPayload(player.getId()));
            }
         }
      }
   }

   private static void handleFounderRegroup(ServerPlayerEntity player) {
      boolean crouching = player.isSneaking();
      executeRegroupCommand(player, player.getPos(), player.getYaw(), crouching);
      String ownerName = player.getName().getString();
      VillagerTransformTracker.clearActiveTarget(ownerName);
      founderTargets.remove(player.getUuid());
      founderStopActive.remove(player.getUuid());
      player.sendMessage(Text.literal(crouching ? "Form up." : "Come.").formatted(Formatting.GOLD, Formatting.BOLD), true);

      for (ServerPlayerEntity nearby : PlayerLookup.tracking(player)) {
         ServerPlayNetworking.send(nearby, new FounderAnimPayload(player.getId()));
      }
   }

   private static void handleFounderStop(ServerPlayerEntity player) {
      if (founderStopActive.contains(player.getUuid())) {
         executeContinueCommand(player);
         founderStopActive.remove(player.getUuid());
         player.sendMessage(Text.literal("You can all move now.").formatted(Formatting.GOLD, Formatting.BOLD), true);
      } else {
         executeStopCommand(player);
         activeStopHeartbeats.remove(player.getUuid());
         String ownerName = player.getName().getString();
         VillagerTransformTracker.clearActiveTarget(ownerName);
         founderTargets.remove(player.getUuid());
         founderStopActive.add(player.getUuid());
         player.sendMessage(Text.literal("Wait.").formatted(Formatting.GOLD, Formatting.BOLD), true);
      }

      for (ServerPlayerEntity nearby : PlayerLookup.tracking(player)) {
         ServerPlayNetworking.send(nearby, new FounderAnimPayload(player.getId()));
      }
   }

   private static void handleFounderSummon(ServerPlayerEntity player) {
      ServerWorld level = player.getServerWorld();
      String ownerName = player.getName().getString();
      if (player.isSneaking()) {
         double radius = 12.0;
         int count = 5;
         double cx = player.getX();
         double cz = player.getZ();

         for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2) / count * i;
            double sx = cx + Math.cos(angle) * radius;
            double sz = cz + Math.sin(angle) * radius;
            int sy = level.getTopY(Type.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(sx), (int)Math.floor(sz));
            float yaw = (float)(Math.toDegrees(Math.atan2(sz - cz, sx - cx)) - 90.0);
            summonStoppedTitan(level, sx, sy, sz, yaw, ownerName);
         }
      } else {
         Vec3d eye = player.getCameraPosVec(1.0F);
         Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(64.0));
         BlockHitResult hit = level.raycast(new RaycastContext(eye, end, ShapeType.COLLIDER, FluidHandling.NONE, player));
         double sx;
         double sy;
         double sz;
         if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            Vec3d loc = hit.getPos();
            sx = loc.x;
            sy = loc.y;
            sz = loc.z;
         } else {
            sx = end.x;
            sz = end.z;
            sy = level.getTopY(Type.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(sx), (int)Math.floor(sz));
         }

         summonStoppedTitan(level, sx, sy, sz, player.getYaw(), ownerName);
      }

      player.sendMessage(Text.literal("Arise.").formatted(Formatting.GOLD, Formatting.BOLD), true);

      for (ServerPlayerEntity nearby : PlayerLookup.tracking(player)) {
         ServerPlayNetworking.send(nearby, new FounderAnimPayload(player.getId()));
      }
   }

   private static void summonStoppedTitan(ServerWorld level, double x, double y, double z, float yaw, String ownerName) {
      HostileEntity titan = VillagerTransformTracker.spawnShiftPureTitan(level, x, y, z, yaw, ownerName, true);
      titan.addCommandTag("dannysaot_founder_summoned");
      titan.addCommandTag("dannysaot_commanded_stop");
      titan.setTarget(null);
   }

   private static void handleFounderToggleScope(ServerPlayerEntity player) {
      boolean nowLocal;
      if (founderLocalMode.contains(player.getUuid())) {
         founderLocalMode.remove(player.getUuid());
         nowLocal = false;
      } else {
         founderLocalMode.add(player.getUuid());
         nowLocal = true;
      }

      player.sendMessage(Text.literal(nowLocal ? "Local command." : "Global command.").formatted(Formatting.GOLD, Formatting.BOLD), true);
   }

   private static void handleFounderDespawn(ServerPlayerEntity player) {
      ServerWorld level = player.getServerWorld();
      String ownerTag = "dannysaot_roar_owner:" + player.getName().getString();
      double radius = 350.0;
      Box box = new Box(
         player.getX() - radius, player.getY() - radius, player.getZ() - radius, player.getX() + radius, player.getY() + radius, player.getZ() + radius
      );
      int removed = 0;

      for (HostileEntity mob : level.getNonSpectatingEntities(HostileEntity.class, box)) {
         if (isCommandTarget(player, mob, ownerTag)) {
            level.spawnParticles(
               ParticleTypes.CLOUD, mob.getX(), mob.getY() + mob.getHeight() * 0.5, mob.getZ(), 30, mob.getWidth(), mob.getHeight() * 0.5, mob.getWidth(), 0.02
            );
            mob.discard();
            removed++;
         }
      }

      player.sendMessage(Text.literal("Despawned " + removed + " titan(s).").formatted(Formatting.GOLD, Formatting.BOLD), true);
   }

   private static void handleFounderGuards(ServerPlayerEntity player) {
      List<UUID> existing = founderGuards.get(player.getUuid());
      if (existing != null && !existing.isEmpty()) {
         for (UUID id : existing) {
            OgreTitanEntity ogre = findOgre(player.server, id);
            if (ogre != null && ogre.isAlive()) {
               ((ServerWorld)ogre.getWorld())
                  .spawnParticles(
                     ParticleTypes.CLOUD,
                     ogre.getX(),
                     ogre.getY() + ogre.getHeight() * 0.5,
                     ogre.getZ(),
                     30,
                     ogre.getWidth(),
                     ogre.getHeight() * 0.5,
                     ogre.getWidth(),
                     0.02
                  );
               ogre.discard();
            }
         }

         founderGuards.remove(player.getUuid());
         player.sendMessage(Text.literal("Guards dismissed.").formatted(Formatting.GOLD, Formatting.BOLD), true);
      } else {
         ServerWorld level = player.getServerWorld();
         float yaw = player.getYaw();
         double yawRad = Math.toRadians(yaw);
         double fx = -Math.sin(yawRad);
         double fz = Math.cos(yawRad);
         double rx = Math.cos(yawRad);
         double rz = Math.sin(yawRad);
         double backDist = 6.0;
         double sideDist = 5.0;
         String ownerTag = "dannysaot_roar_owner:" + player.getName().getString();
         List<UUID> guards = new ArrayList<>();

         for (int side = -1; side <= 1; side += 2) {
            double gx = player.getX() - fx * backDist + rx * sideDist * side;
            double gz = player.getZ() - fz * backDist + rz * sideDist * side;
            double gy = player.getY();
            spawnShiftLightning(level, gx, gy, gz);
            OgreTitanEntity ogre = new OgreTitanEntity(DannysAot.OGRE_TITAN, level);
            ogre.setPosition(gx, gy, gz);
            ogre.setYaw(yaw);
            ogre.bodyYaw = yaw;
            ogre.setHeadYaw(yaw);
            ogre.prevYaw = yaw;
            ogre.setCommandFacing(yaw);
            ogre.initialize(level, level.getLocalDifficulty(BlockPos.ofFloored(gx, gy, gz)), SpawnReason.MOB_SUMMONED, null, null);
            ogre.addCommandTag(ownerTag);
            ogre.addCommandTag("dannysaot_founder_summoned");
            ogre.addCommandTag("dannysaot_commanded_stop");
            ogre.setTarget(null);
            level.spawnEntity(ogre);
            VillagerTransformSpawnPayload spawnFx = new VillagerTransformSpawnPayload(gx, gy, gz, ogre.getId());

            for (ServerPlayerEntity nearby : PlayerLookup.tracking(level, BlockPos.ofFloored(gx, gy, gz))) {
               ServerPlayNetworking.send(nearby, spawnFx);
            }

            guards.add(ogre.getUuid());
         }

         founderGuards.put(player.getUuid(), guards);
         player.sendMessage(Text.literal("Guards summoned.").formatted(Formatting.GOLD, Formatting.BOLD), true);
      }
   }

   private static OgreTitanEntity findOgre(MinecraftServer server, UUID id) {
      for (ServerWorld lvl : server.getWorlds()) {
         if (lvl.getEntity(id) instanceof OgreTitanEntity ogre) {
            return ogre;
         }
      }

      return null;
   }

   private static void tickFounderGuards(MinecraftServer server) {
      if (!founderGuards.isEmpty()) {
         Iterator<Entry<UUID, List<UUID>>> it = founderGuards.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, List<UUID>> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null && player.getCommandTags().contains("founder")) {
               boolean hunting = founderTargets.containsKey(player.getUuid());
               float yaw = player.getYaw();
               double yawRad = Math.toRadians(yaw);
               double fx = -Math.sin(yawRad);
               double fz = Math.cos(yawRad);
               double rx = Math.cos(yawRad);
               double rz = Math.sin(yawRad);
               double backDist = 6.0;
               double sideDist = 5.0;
               List<UUID> guards = entry.getValue();
               int idx = 0;
               Iterator<UUID> gi = guards.iterator();

               while (gi.hasNext()) {
                  UUID id = gi.next();
                  OgreTitanEntity ogre = findOgre(server, id);
                  if (ogre == null || !ogre.isAlive()) {
                     gi.remove();
                  } else if (hunting) {
                     idx++;
                  } else if (ogre.hasGuardRetaliation()) {
                     ogre.removeScoreboardTag("dannysaot_commanded_stop");
                     VillagerTransformTracker.clearRegroup(ogre);
                     idx++;
                  } else {
                     int side = idx == 0 ? -1 : 1;
                     double sx = player.getX() - fx * backDist + rx * sideDist * side;
                     double sz = player.getZ() - fz * backDist + rz * sideDist * side;
                     double dx = sx - ogre.getX();
                     double dz = sz - ogre.getZ();
                     if (dx * dx + dz * dz > 9.0) {
                        ogre.removeScoreboardTag("dannysaot_commanded_stop");
                        VillagerTransformTracker.setRegroupTarget(ogre, sx, sz, yaw);
                     } else {
                        VillagerTransformTracker.clearRegroup(ogre);
                        ogre.addCommandTag("dannysaot_commanded_stop");
                        ogre.setCommandFacing(yaw);
                     }

                     idx++;
                  }
               }

               if (guards.isEmpty()) {
                  it.remove();
               }
            } else {
               for (UUID id : entry.getValue()) {
                  OgreTitanEntity ogre = findOgre(server, id);
                  if (ogre != null) {
                     ogre.discard();
                  }
               }

               it.remove();
            }
         }
      }
   }

   private static void handleFounderMount(ServerPlayerEntity player) {
      if (player.getVehicle() instanceof SadTitanEntity sad) {
         player.stopRiding();
         sad.discard();
         player.sendMessage(Text.literal("Dismounted.").formatted(Formatting.GOLD, Formatting.BOLD), true);
      } else if (player.getVehicle() instanceof AttackTitanEntity) {
         player.sendMessage(Text.literal("Can't mount while in titan form.").formatted(Formatting.RED), true);
      } else {
         ServerWorld level = player.getServerWorld();
         SadTitanEntity sadTitan = new SadTitanEntity(DannysAot.SAD_TITAN, level);
         sadTitan.setRideable(true);
         float yawRad = (float)Math.toRadians(player.getYaw());
         double spawnX = player.getX() - Math.sin(yawRad) * 2.0;
         double spawnZ = player.getZ() + Math.cos(yawRad) * 2.0;
         sadTitan.setPosition(spawnX, player.getY(), spawnZ);
         sadTitan.setYaw(player.getYaw());
         sadTitan.bodyYaw = player.getYaw();
         level.spawnEntity(sadTitan);
         player.startRiding(sadTitan, true);
         player.sendMessage(Text.literal("Mounted Sad Titan.").formatted(Formatting.GOLD, Formatting.BOLD), true);
      }
   }

   private static boolean isTitanHitboxPart(Entity e) {
      String n = e.getClass().getSimpleName();
      return n.endsWith("EyeEntity") || n.endsWith("NapeEntity") || n.endsWith("GrabEntity");
   }

   private static void cancelTitanEating(HostileEntity mob) {
      if (mob instanceof SmallTitanEntity t) {
         if (t.isEating()) {
            t.cancelEating();
         }
      } else if (mob instanceof SmallTitan2Entity tx) {
         if (tx.isEating()) {
            tx.cancelEating();
         }
      } else if (mob instanceof TitanEntity txx) {
         if (txx.isEating()) {
            txx.cancelEating();
         }
      } else if (mob instanceof FritzTitanEntity txxx) {
         if (txxx.isEating()) {
            txxx.cancelEating();
         }
      } else if (mob instanceof ConnieFatherEntity txxxx) {
         if (txxxx.isEating()) {
            txxxx.cancelEating();
         }
      } else if (mob instanceof SadTitanEntity txxxxx) {
         if (txxxxx.isEating()) {
            txxxxx.cancelEating();
         }
      } else if (mob instanceof YellowTitanEntity txxxxxx) {
         if (txxxxxx.isEating()) {
            txxxxxx.cancelEating();
         }
      } else if (mob instanceof OgreTitanEntity txxxxxxx && txxxxxxx.isEating()) {
         txxxxxxx.cancelEating();
      }
   }

   private static boolean isCommandTarget(ServerPlayerEntity player, HostileEntity mob, String ownerTag) {
      if (player.getCommandTags().contains("founder") && founderLocalMode.contains(player.getUuid())) {
         return isCommandablePureTitan(mob) && mob.getCommandTags().contains("dannysaot_founder_summoned") && mob.getCommandTags().contains(ownerTag);
      } else {
         return shouldCommandAllPures(player) ? isCommandablePureTitan(mob) : mob.getCommandTags().contains(ownerTag);
      }
   }

   private static void tickFounderTarget(MinecraftServer server) {
      if (!founderTargets.isEmpty()) {
         Iterator<Entry<UUID, ModNetworking.FounderTarget>> it = founderTargets.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, ModNetworking.FounderTarget> entry = it.next();
            ModNetworking.FounderTarget ft = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null && player.getCommandTags().contains("founder")) {
               Entity target = null;

               for (ServerWorld lvl : server.getWorlds()) {
                  target = lvl.getEntity(ft.targetUUID());
                  if (target != null) {
                     break;
                  }
               }

               if (target == null || !target.isAlive()) {
                  VillagerTransformTracker.clearActiveTarget(ft.ownerName());
                  it.remove();
                  player.sendMessage(Text.literal("Target eliminated.").formatted(Formatting.GREEN), true);
               }
            } else {
               VillagerTransformTracker.clearActiveTarget(ft.ownerName());
               it.remove();
            }
         }
      }
   }

   private static void dispatchBeastRoyalAbility(ServerPlayerEntity player, int abilityNumber) {
      if (abilityNumber >= 5 && abilityNumber <= 9) {
         if (player.getCommandTags().contains("beast")) {
            BloodlineData bd = BloodlineData.get(player.getServerWorld());
            if (bd.getBloodline(player.getUuid()) != BloodlineType.ROYAL) {
               long now = System.currentTimeMillis();
               Long lastReject = nonRoyalRejectCooldowns.get(player.getUuid());
               if (lastReject == null || now - lastReject >= 1000L) {
                  nonRoyalRejectCooldowns.put(player.getUuid(), now);
                  player.sendMessage(Text.literal("Only Royals can use this Ability").formatted(Formatting.GOLD, Formatting.BOLD), false);
               }
            } else if (abilityNumber == 5) {
               handleTitanRoar(player);
            } else {
               handleBeastRoyalCommands(player, abilityNumber - 5);
            }
         }
      }
   }

   private static void handleBeastRoyalCommands(ServerPlayerEntity player, int abilityNumber) {
      if (player.getCommandTags().contains("beast")) {
         BloodlineData bd = BloodlineData.get(player.getServerWorld());
         if (bd.getBloodline(player.getUuid()) == BloodlineType.ROYAL) {
            Integer blockUntil = roarBlockTicks.get(player.getUuid());
            if (blockUntil != null && player.server.getTicks() < blockUntil) {
               player.sendMessage(Text.literal("Cannot command during Awaken").styled(style -> style.withColor(Formatting.RED)), true);
               return;
            }

            if (!awakenActive.contains(player.getUuid())) {
               return;
            }

            long now = System.currentTimeMillis();
            if (abilityNumber == 1) {
               Long lastStop = stopCommandCooldowns.get(player.getUuid());
               if (lastStop != null && now - lastStop < 2000L) {
                  return;
               }

               stopCommandCooldowns.put(player.getUuid(), now);
               player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.STOP_COMMAND, SoundCategory.PLAYERS, 4.0F, 1.0F);
               broadcastGoldMessage(player, "Wait.");
               ServerPlayNetworking.send(player, new EffectPayload("command_stop", player.getX(), player.getY(), player.getZ(), 1.0F));
               int executeTick = player.server.getTicks() + 10;
               pendingCommands.add(new ModNetworking.PendingCommand(player.getUuid(), "stop", executeTick));
            } else if (abilityNumber == 2) {
               Long lastContinue = continueCommandCooldowns.get(player.getUuid());
               if (lastContinue != null && now - lastContinue < 2000L) {
                  return;
               }

               continueCommandCooldowns.put(player.getUuid(), now);
               player.getServerWorld()
                  .playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CONTINUE_COMMAND, SoundCategory.PLAYERS, 4.0F, 1.0F);
               broadcastGoldMessage(player, "You can all move now.");
               ServerPlayNetworking.send(player, new EffectPayload("command_continue", player.getX(), player.getY(), player.getZ(), 1.0F));
               int executeTick = player.server.getTicks() + 30;
               pendingCommands.add(new ModNetworking.PendingCommand(player.getUuid(), "continue", executeTick));
            } else if (abilityNumber == 3) {
               Long lastStop = stopCommandCooldowns.get(player.getUuid());
               if (lastStop != null && now - lastStop < 2000L) {
                  return;
               }

               stopCommandCooldowns.put(player.getUuid(), now);
               player.getServerWorld()
                  .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_RAVAGER_STUNNED, SoundCategory.PLAYERS, 4.0F, 0.9F);
               broadcastGoldMessage(player, "Come.");
               ServerPlayNetworking.send(player, new EffectPayload("command_stop", player.getX(), player.getY(), player.getZ(), 1.0F));
               int executeTick = player.server.getTicks() + 10;
               boolean crouchingAtTrigger = player.isSneaking();
               pendingCommands.add(
                  new ModNetworking.PendingCommand(player.getUuid(), "regroup", executeTick, player.getPos(), player.getYaw(), crouchingAtTrigger)
               );
            } else if (abilityNumber == 4) {
               ModNetworking.TargetMode mode = targetModes.getOrDefault(player.getUuid(), ModNetworking.TargetMode.NONE);
               if (mode == ModNetworking.TargetMode.NONE) {
                  targetModes.put(player.getUuid(), ModNetworking.TargetMode.SELECTING);
                  ServerPlayNetworking.send(player, new TargetModeSyncPayload("selecting"));
               } else if (mode == ModNetworking.TargetMode.SELECTING) {
                  Integer highlightedId = highlightedTargetIds.get(player.getUuid());
                  Entity looked = null;
                  if (highlightedId != null) {
                     Entity h = player.getServerWorld().getEntityById(highlightedId);
                     if (h != null && h.isAlive() && !isPureTitanOrHitbox(h)) {
                        looked = h;
                     }
                  }

                  if (looked == null) {
                     looked = findEntityInAimCone(player, 200.0, 0.85, e -> e instanceof LivingEntity && !isPureTitanOrHitbox(e));
                  }

                  if (looked != null) {
                     String targetName = looked.getName().getString();
                     String ownerName = player.getName().getString();
                     activeTargetUUIDs.put(player.getUuid(), looked.getUuid());
                     activeTargetNames.put(player.getUuid(), targetName);
                     targetModes.put(player.getUuid(), ModNetworking.TargetMode.ACTIVE);
                     VillagerTransformTracker.setActiveTarget(ownerName, looked.getUuid(), targetName);
                     if (shouldCommandAllPures(player)) {
                        String ownerTag = "dannysaot_roar_owner:" + ownerName;
                        Box tagBox = new Box(
                           player.getX() - 350.0,
                           player.getY() - 350.0,
                           player.getZ() - 350.0,
                           player.getX() + 350.0,
                           player.getY() + 350.0,
                           player.getZ() + 350.0
                        );

                        for (HostileEntity mob : player.getServerWorld().getNonSpectatingEntities(HostileEntity.class, tagBox)) {
                           if (isCommandablePureTitan(mob) && !mob.getCommandTags().contains(ownerTag)) {
                              mob.addCommandTag(ownerTag);
                           }
                        }
                     }

                     broadcastGoldMessage(player, "Kill " + targetName + ".");
                     ServerPlayNetworking.send(player, new TargetGlowPayload(new int[0]));
                     ServerPlayNetworking.send(player, new TargetModeSyncPayload("active"));
                     if (looked instanceof ServerPlayerEntity targetPlayer) {
                        String ownerTag = "dannysaot_roar_owner:" + ownerName;
                        int titanCount = 0;

                        for (ServerWorld sl : player.server.getWorlds()) {
                           Box countBox = new Box(
                              player.getX() - 500.0,
                              player.getY() - 500.0,
                              player.getZ() - 500.0,
                              player.getX() + 500.0,
                              player.getY() + 500.0,
                              player.getZ() + 500.0
                           );

                           for (HostileEntity mobx : sl.getNonSpectatingEntities(HostileEntity.class, countBox)) {
                              if (mobx.getCommandTags().contains(ownerTag)) {
                                 titanCount++;
                              }
                           }
                        }

                        String huntMsg = titanCount == 1 ? "An Abnormal has started hunting you!" : titanCount + " Abnormals have started hunting you!";
                        targetPlayer.sendMessage(Text.literal(huntMsg).formatted(Formatting.RED, Formatting.BOLD));
                     }
                  } else {
                     targetModes.remove(player.getUuid());
                     highlightedTargetIds.remove(player.getUuid());
                     ServerPlayNetworking.send(player, new TargetGlowPayload(new int[0]));
                     ServerPlayNetworking.send(player, new TargetModeSyncPayload("none"));
                  }
               } else if (mode == ModNetworking.TargetMode.ACTIVE) {
                  String ownerNamex = player.getName().getString();
                  VillagerTransformTracker.clearActiveTarget(ownerNamex);
                  targetModes.remove(player.getUuid());
                  activeTargetUUIDs.remove(player.getUuid());
                  activeTargetNames.remove(player.getUuid());
                  ServerPlayNetworking.send(player, new TargetModeSyncPayload("none"));
               }
            }
         }
      }
   }

   private static void tickPendingCommands(MinecraftServer server) {
      if (!pendingCommands.isEmpty()) {
         int currentTick = server.getTicks();
         Iterator<ModNetworking.PendingCommand> it = pendingCommands.iterator();

         while (it.hasNext()) {
            ModNetworking.PendingCommand cmd = it.next();
            if (currentTick >= cmd.executeTick()) {
               it.remove();
               ServerPlayerEntity player = server.getPlayerManager().getPlayer(cmd.playerUUID());
               if (player != null) {
                  if ("stop".equals(cmd.command())) {
                     executeStopCommand(player);
                  } else if ("continue".equals(cmd.command())) {
                     executeContinueCommand(player);
                  } else if ("regroup".equals(cmd.command())) {
                     executeRegroupCommand(player, cmd.position(), cmd.yaw(), cmd.crouching());
                  }
               }
            }
         }
      }
   }

   private static void tickStopHeartbeat(MinecraftServer server) {
      if (!activeStopHeartbeats.isEmpty()) {
         int currentTick = server.getTicks();
         Iterator<Entry<UUID, Integer>> it = activeStopHeartbeats.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Integer> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
               it.remove();
            } else if (currentTick - entry.getValue() >= 14) {
               entry.setValue(currentTick);
               double radius = 350.0;
               Box area = new Box(
                  player.getX() - radius,
                  player.getY() - radius,
                  player.getZ() - radius,
                  player.getX() + radius,
                  player.getY() + radius,
                  player.getZ() + radius
               );

               for (ServerPlayerEntity nearby : player.getServerWorld().getNonSpectatingEntities(ServerPlayerEntity.class, area)) {
                  nearby.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 0.7F, 1.0F);
               }
            }
         }
      }
   }

   private static boolean shouldCommandAllPures(ServerPlayerEntity player) {
      return ModCommands.isRoyalBeastEnabled(player.getUuid()) || player.getCommandTags().contains("founder");
   }

   private static boolean isCommandablePureTitan(Entity e) {
      return e instanceof TitanEntity
         || e instanceof SmallTitanEntity
         || e instanceof SmallTitan2Entity
         || e instanceof FritzTitanEntity
         || e instanceof ConnieFatherEntity
         || e instanceof SadTitanEntity
         || e instanceof YellowTitanEntity
         || e instanceof OgreTitanEntity;
   }

   private static void executeStopCommand(ServerPlayerEntity player) {
      String playerName = player.getName().getString();
      String ownerTag = "dannysaot_roar_owner:" + playerName;
      String stopTag = "dannysaot_commanded_stop";
      ServerWorld level = player.getServerWorld();
      double radius = 350.0;
      Box searchBox = new Box(
         player.getX() - radius, player.getY() - radius, player.getZ() - radius, player.getX() + radius, player.getY() + radius, player.getZ() + radius
      );

      for (HostileEntity mob : level.getNonSpectatingEntities(HostileEntity.class, searchBox)) {
         if (isCommandTarget(player, mob, ownerTag)) {
            if (mob instanceof SmallTitanEntity st) {
               st.setSitting(false);
            } else if (mob instanceof SmallTitan2Entity st2) {
               st2.setSitting(false);
            }

            mob.addCommandTag(stopTag);
            mob.setTarget(null);
         }
      }

      activeStopHeartbeats.put(player.getUuid(), player.server.getTicks());
   }

   private static void executeContinueCommand(ServerPlayerEntity player) {
      String playerName = player.getName().getString();
      String ownerTag = "dannysaot_roar_owner:" + playerName;
      String stopTag = "dannysaot_commanded_stop";
      ServerWorld level = player.getServerWorld();
      double radius = 350.0;
      Box searchBox = new Box(
         player.getX() - radius, player.getY() - radius, player.getZ() - radius, player.getX() + radius, player.getY() + radius, player.getZ() + radius
      );

      for (HostileEntity mob : level.getNonSpectatingEntities(HostileEntity.class, searchBox)) {
         if (isCommandTarget(player, mob, ownerTag)) {
            if (mob instanceof SmallTitanEntity st) {
               st.setSitting(false);
            } else if (mob instanceof SmallTitan2Entity st2) {
               st2.setSitting(false);
            }

            if (VillagerTransformTracker.isRegrouping(mob)) {
               VillagerTransformTracker.clearRegroup(mob);
            }

            if (mob.getCommandTags().contains(stopTag)) {
               mob.removeScoreboardTag(stopTag);
            }

            mob.removeScoreboardTag("dannysaot_regroup_no_collision");
         }
      }

      activeStopHeartbeats.remove(player.getUuid());
   }

   private static void executeRegroupCommand(ServerPlayerEntity player, Vec3d regroupCenter, float facingYaw, boolean crouching) {
      if (regroupCenter == null) {
         regroupCenter = player.getPos();
      }

      String playerName = player.getName().getString();
      String ownerTag = "dannysaot_roar_owner:" + playerName;
      String stopTag = "dannysaot_commanded_stop";
      ServerWorld level = player.getServerWorld();
      double radius = 350.0;
      Box searchBox = new Box(
         player.getX() - radius, player.getY() - radius, player.getZ() - radius, player.getX() + radius, player.getY() + radius, player.getZ() + radius
      );
      List<HostileEntity> chosen = new ArrayList<>();

      for (HostileEntity mob : level.getNonSpectatingEntities(HostileEntity.class, searchBox)) {
         if (isCommandTarget(player, mob, ownerTag)) {
            if (mob instanceof SmallTitanEntity st) {
               st.setSitting(false);
            } else if (mob instanceof SmallTitan2Entity st2) {
               st2.setSitting(false);
            }

            mob.removeScoreboardTag(stopTag);
            if (mob instanceof TitanEntity titan) {
               titan.cancelEating();
            } else if (mob instanceof FritzTitanEntity fritz) {
               fritz.cancelEating();
            } else if (mob instanceof SmallTitanEntity small) {
               small.cancelEating();
            } else if (mob instanceof SmallTitan2Entity small2) {
               small2.cancelEating();
            } else if (mob instanceof OgreTitanEntity ogre) {
               ogre.cancelEating();
            }

            mob.setTarget(null);
            chosen.add(mob);
         }
      }

      Vec3d finalCenter = regroupCenter;
      chosen.sort(
         (a, b) -> Double.compare(
            a.squaredDistanceTo(finalCenter.getX(), a.getY(), finalCenter.getZ()), b.squaredDistanceTo(finalCenter.getX(), b.getY(), finalCenter.getZ())
         )
      );
      if (crouching) {
         assignCrescentRegroupTargets(chosen, regroupCenter, facingYaw);
      } else {
         Random rand = new Random();

         for (HostileEntity mobx : chosen) {
            double angle = rand.nextDouble() * Math.PI * 2.0;
            double distance = 5.0 + rand.nextDouble() * 5.0;
            double targetX = regroupCenter.getX() + Math.cos(angle) * distance;
            double targetZ = regroupCenter.getZ() + Math.sin(angle) * distance;
            VillagerTransformTracker.setRegroupTarget(mobx, targetX, targetZ, facingYaw);
            mobx.addCommandTag("dannysaot_regroup_no_collision");
         }
      }

      activeStopHeartbeats.remove(player.getUuid());
   }

   private static void assignCrescentRegroupTargets(List<HostileEntity> mobs, Vec3d center, float facingYaw) {
      if (!mobs.isEmpty()) {
         double yawRad = Math.toRadians(facingYaw);
         double forwardX = -Math.sin(yawRad);
         double forwardZ = Math.cos(yawRad);
         double rightX = Math.cos(yawRad);
         double rightZ = Math.sin(yawRad);
         double SHOULDER_GAP = 12.0;
         double BASE_OFFSET = 6.0;
         double DEPTH_PER_PAIR = 3.0;

         for (int i = 0; i < mobs.size(); i++) {
            int pair = i / 2;
            boolean rightSide = i % 2 == 1;
            double sideways = 6.0 + pair * 12.0;
            if (!rightSide) {
               sideways = -sideways;
            }

            double forward = pair * 3.0;
            double targetX = center.getX() + rightX * sideways + forwardX * forward;
            double targetZ = center.getZ() + rightZ * sideways + forwardZ * forward;
            HostileEntity mob = mobs.get(i);
            VillagerTransformTracker.setRegroupTarget(mob, targetX, targetZ, facingYaw);
            mob.addCommandTag("dannysaot_regroup_no_collision");
         }
      }
   }

   public static void broadcastBloodlineToAll(ServerPlayerEntity player, int bloodlineOrdinal) {
      BloodlineSyncPayload payload = new BloodlineSyncPayload(player.getUuid(), bloodlineOrdinal);

      for (ServerPlayerEntity other : PlayerLookup.world(player.getServerWorld())) {
         ServerPlayNetworking.send(other, payload);
      }
   }

   public static void syncAllBloodlinesToPlayer(ServerPlayerEntity joiningPlayer, MinecraftServer server, BloodlineData bloodlineData) {
      for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
         if (other != joiningPlayer && bloodlineData.hasBloodline(other.getUuid())) {
            BloodlineType type = bloodlineData.getBloodline(other.getUuid());
            ServerPlayNetworking.send(joiningPlayer, new BloodlineSyncPayload(other.getUuid(), type.ordinal()));
         }
      }
   }

   private static void broadcastGoldMessage(ServerPlayerEntity source, String message) {
      String name = source.getCommandTags().contains("titan_stealth") ? "Beast Titan" : source.getName().getString();
      Text msg = Text.literal("<" + name + "> " + message).formatted(Formatting.GOLD, Formatting.BOLD);
      double radius = 200.0;
      Box area = new Box(
         source.getX() - radius, source.getY() - radius, source.getZ() - radius, source.getX() + radius, source.getY() + radius, source.getZ() + radius
      );

      for (ServerPlayerEntity nearby : source.getServerWorld().getNonSpectatingEntities(ServerPlayerEntity.class, area)) {
         nearby.sendMessage(msg);
      }
   }

   private static boolean isPureTitanOrHitbox(Entity entity) {
      return entity instanceof TitanEntity
         || entity instanceof SmallTitanEntity
         || entity instanceof SmallTitan2Entity
         || entity instanceof FritzTitanEntity
         || entity instanceof ConnieFatherEntity
         || entity instanceof TitanEyeEntity
         || entity instanceof TitanNapeEntity
         || entity instanceof SmallTitanEyeEntity
         || entity instanceof SmallTitanNapeEntity
         || entity instanceof SmallTitan2EyeEntity
         || entity instanceof SmallTitan2NapeEntity
         || entity instanceof FritzTitanEyeEntity
         || entity instanceof FritzTitanNapeEntity
         || entity instanceof ConnieFatherEyeEntity
         || entity instanceof ConnieFatherNapeEntity;
   }

   private static Entity getEntityPlayerIsLookingAt(ServerPlayerEntity player, double range) {
      Vec3d eye = player.getEyePos();
      Vec3d look = player.getRotationVector();
      Vec3d end = eye.add(look.multiply(range));
      Box searchBox = player.getBoundingBox().stretch(look.multiply(range)).expand(1.0);
      double closestDist = Double.MAX_VALUE;
      Entity closest = null;

      for (Entity entity : player.getServerWorld().getOtherEntities(player, searchBox, e -> !e.isSpectator() && e.canHit() && !isPureTitanOrHitbox(e))) {
         Box entityBox = entity.getBoundingBox().expand(0.3);
         Optional<Vec3d> optional = entityBox.raycast(eye, end);
         if (optional.isPresent()) {
            double dist = eye.squaredDistanceTo(optional.get());
            if (dist < closestDist) {
               closestDist = dist;
               closest = entity;
            }
         }
      }

      return closest;
   }

   private static Entity findEntityInAimCone(ServerPlayerEntity player, double maxRange, double minDot, Predicate<Entity> filter) {
      Vec3d eye = player.getEyePos();
      Vec3d look = player.getRotationVector().normalize();
      Box searchBox = new Box(
         player.getX() - maxRange,
         player.getY() - maxRange,
         player.getZ() - maxRange,
         player.getX() + maxRange,
         player.getY() + maxRange,
         player.getZ() + maxRange
      );
      Entity best = null;
      double bestScore = Double.MAX_VALUE;

      for (Entity candidate : player.getServerWorld().getOtherEntities(player, searchBox, e -> e.isSpectator() ? false : filter.test(e))) {
         Vec3d toCandidate = candidate.getBoundingBox().getCenter().subtract(eye);
         double dist = toCandidate.length();
         if (!(dist > maxRange) && !(dist < 0.001)) {
            Vec3d dirNorm = toCandidate.multiply(1.0 / dist);
            double dot = look.dotProduct(dirNorm);
            if (!(dot < minDot)) {
               double score = dist * (1.0 - dot * 0.5);
               if (score < bestScore) {
                  bestScore = score;
                  best = candidate;
               }
            }
         }
      }

      return best;
   }

   private static void tickTargetAbility(MinecraftServer server) {
      Iterator<Entry<UUID, ModNetworking.TargetMode>> it = targetModes.entrySet().iterator();

      while (it.hasNext()) {
         Entry<UUID, ModNetworking.TargetMode> entry = it.next();
         ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
         if (player == null) {
            it.remove();
         } else {
            ModNetworking.TargetMode mode = entry.getValue();
            if (mode == ModNetworking.TargetMode.SELECTING) {
               Entity looked = findEntityInAimCone(player, 200.0, 0.85, e -> e instanceof LivingEntity && !isPureTitanOrHitbox(e));
               int[] glowIds;
               if (looked != null) {
                  highlightedTargetIds.put(player.getUuid(), looked.getId());
                  glowIds = new int[]{looked.getId()};
               } else {
                  highlightedTargetIds.remove(player.getUuid());
                  glowIds = new int[0];
               }

               ServerPlayNetworking.send(player, new TargetGlowPayload(glowIds));
               player.sendMessage(Text.literal("Look at target and press again.").formatted(Formatting.GREEN, Formatting.BOLD), true);
            } else if (mode == ModNetworking.TargetMode.ACTIVE) {
               String targetName = activeTargetNames.get(player.getUuid());
               UUID targetUUID = activeTargetUUIDs.get(player.getUuid());
               if (targetUUID != null) {
                  Entity target = player.getServerWorld().getEntity(targetUUID);
                  if (target == null || !target.isAlive()) {
                     String ownerName = player.getName().getString();
                     VillagerTransformTracker.clearActiveTarget(ownerName);
                     it.remove();
                     activeTargetUUIDs.remove(player.getUuid());
                     activeTargetNames.remove(player.getUuid());
                     ServerPlayNetworking.send(player, new TargetGlowPayload(new int[0]));
                     ServerPlayNetworking.send(player, new TargetModeSyncPayload("none"));
                     continue;
                  }
               }

               player.sendMessage(
                  Text.literal("Active Target: " + (targetName != null ? targetName : "Unknown")).formatted(Formatting.GREEN, Formatting.BOLD), true
               );
               ServerPlayNetworking.send(player, new TargetGlowPayload(new int[0]));
            }
         }
      }
   }

   private static void tickBeastGrabHighlight(MinecraftServer server) {
      for (ServerPlayerEntity shifter : server.getPlayerManager().getPlayerList()) {
         if (shifter.getVehicle() instanceof BeastTitanEntity beastTitan) {
            UUID shifterUUID = beastTitan.getShifterUUID();
            if (shifterUUID != null && shifterUUID.equals(shifter.getUuid())) {
               if (beastTitan.isThrowTurning() && beastTitan.getRockThrowPhase() == 0 && beastTitan.getGrabbedEntityId() == -1) {
                  if (!beastTitan.isTitanAttacking() && !beastTitan.isRoaring() && !beastTitan.isTransforming() && !beastTitan.isDismounting()) {
                     Entity best = findEntityInAimCone(
                        shifter, 20.0, 0.85, e -> e != shifter && e.getVehicle() != beastTitan ? BeastTitanEntity.canBeGrabbed(e) : false
                     );
                     int newHighlight = best != null ? best.getId() : -1;
                     int oldHighlight = beastTitan.getHighlightedEntityId();
                     if (newHighlight != oldHighlight) {
                        beastTitan.setHighlightedEntityId(newHighlight);
                        if (newHighlight != -1) {
                           ServerPlayNetworking.send(shifter, new TargetGlowPayload(new int[]{newHighlight}));
                        } else {
                           ServerPlayNetworking.send(shifter, new TargetGlowPayload(new int[0]));
                        }
                     }
                  } else if (beastTitan.getHighlightedEntityId() != -1) {
                     beastTitan.setHighlightedEntityId(-1);
                     ServerPlayNetworking.send(shifter, new TargetGlowPayload(new int[0]));
                  }
               } else if (beastTitan.getHighlightedEntityId() != -1) {
                  beastTitan.setHighlightedEntityId(-1);
                  ServerPlayNetworking.send(shifter, new TargetGlowPayload(new int[0]));
               }
            }
         }
      }
   }

   private static void handleTitanShift(ServerPlayerEntity player, ServerWorld level) {
      if (!player.isSpectator()) {
         if (HandcuffsTracker.isCuffed(player.getUuid())) {
            player.sendMessage(Text.literal("Cannot transform while cuffed"), true);
         } else {
            Entity wireVehicle = player.getVehicle();
            if (wireVehicle instanceof ShifterTitan && StrwsRestraintTracker.getWireCount(wireVehicle.getUuid()) >= 1) {
               player.sendMessage(Text.literal("Restrained by wires - can't exit!").formatted(Formatting.RED), true);
            } else if (player.getVehicle() instanceof ColossalTitanEntity titan) {
               if (titan.isDismounting()) {
                  titan.cancelDismounting(player);
               } else {
                  titan.startDismounting(player);
               }
            } else if (player.getVehicle() instanceof AttackTitanEntity attackTitan) {
               if (attackTitan.isDismounting()) {
                  attackTitan.cancelDismounting(player);
               } else {
                  attackTitan.startDismounting(player);
               }
            } else if (player.getVehicle() instanceof ArmoredTitanEntity armoredTitan) {
               if (armoredTitan.isDismounting()) {
                  armoredTitan.cancelDismounting(player);
               } else {
                  armoredTitan.startDismounting(player);
               }
            } else if (player.getVehicle() instanceof FemaleTitanEntity femaleTitan) {
               if (femaleTitan.isDismounting()) {
                  femaleTitan.cancelDismounting(player);
               } else {
                  femaleTitan.startDismounting(player);
               }
            } else if (player.getVehicle() instanceof BeastTitanEntity beastTitan) {
               if (beastTitan.isDismounting()) {
                  beastTitan.cancelDismounting(player);
               } else {
                  beastTitan.startDismounting(player);
               }
            } else if (player.getVehicle() instanceof WarhammerTitanEntity warhammerTitan) {
               if (warhammerTitan.isDismounting()) {
                  warhammerTitan.cancelDismounting(player);
               } else {
                  warhammerTitan.startDismounting(player);
               }
            } else if (player.getVehicle() instanceof FemaleCrystalShellEntity) {
               player.sendMessage(Text.literal("Cannot transform while crystallized"), true);
            } else if (!pendingShifts.containsKey(player.getUuid())) {
               Identifier pathsDim = new Identifier("dannys-aot", "paths");
               if (player.getWorld().getRegistryKey().getValue().equals(pathsDim)) {
                  player.sendMessage(Text.literal("Cannot transform in the Paths"), true);
               } else {
                  if (player.hasStatusEffect(StatusEffects.WEAKNESS)) {
                     StatusEffectInstance weaknessEffect = player.getStatusEffect(StatusEffects.WEAKNESS);
                     if (weaknessEffect != null && weaknessEffect.getAmplifier() >= 4) {
                        player.sendMessage(Text.literal("Too exhausted to transform"), true);
                        return;
                     }
                  }

                  boolean hasColossalTag = PowerAuthority.hasShifter(player, TitanPowerType.COLOSSAL);
                  boolean hasAttackTag = PowerAuthority.hasShifter(player, TitanPowerType.ATTACK);
                  boolean hasArmoredTag = PowerAuthority.hasShifter(player, TitanPowerType.ARMORED);
                  boolean hasBeastTag = PowerAuthority.hasShifter(player, TitanPowerType.BEAST);
                  boolean hasFemaleTag = PowerAuthority.hasShifter(player, TitanPowerType.FEMALE);
                  boolean hasWarhammerTag = PowerAuthority.hasShifter(player, TitanPowerType.WARHAMMER);
                  boolean hasJawShifterTag = PowerAuthority.hasShifter(player, TitanPowerType.JAW);
                  boolean hasFounderTag = player.getCommandTags().contains("founder");
                  boolean hasTripleTTag = player.getCommandTags().contains("triple_t");
                  boolean hasOgreShifterTag = player.getCommandTags().contains("ogre_shifter");
                  boolean hasCartShifterTag = player.getCommandTags().contains("cart_shifter");
                  if (!hasColossalTag
                     && !hasAttackTag
                     && !hasArmoredTag
                     && !hasBeastTag
                     && !hasFemaleTag
                     && !hasWarhammerTag
                     && !hasFounderTag
                     && !hasTripleTTag
                     && !hasOgreShifterTag
                     && !hasJawShifterTag
                     && !hasCartShifterTag) {
                     playerStamina.remove(player.getUuid());
                     player.sendMessage(Text.literal("No shifter powers"), true);
                  } else {
                     float playerMax = getMaxStaminaForPlayer(player);
                     playerMaxStamina.put(player.getUuid(), playerMax);
                     float stamina = playerStamina.getOrDefault(player.getUuid(), playerMax);
                     if (stamina < playerMax * 0.5F) {
                        player.sendMessage(Text.literal("Not enough stamina to transform"), true);
                     } else {
                        ModNetworking.ShifterType shifterType;
                        if (hasJawShifterTag) {
                           shifterType = ModNetworking.ShifterType.JAW;
                        } else if (hasCartShifterTag) {
                           shifterType = ModNetworking.ShifterType.CART;
                        } else if (hasOgreShifterTag) {
                           shifterType = ModNetworking.ShifterType.OGRE_SHIFTER;
                        } else if (hasFounderTag) {
                           shifterType = ModNetworking.ShifterType.FOUNDING;
                        } else if (hasTripleTTag) {
                           shifterType = ModNetworking.ShifterType.TRIPLE_T;
                        } else if (hasAttackTag) {
                           shifterType = ModNetworking.ShifterType.ATTACK;
                        } else if (hasFemaleTag) {
                           shifterType = ModNetworking.ShifterType.FEMALE;
                        } else if (hasArmoredTag) {
                           shifterType = ModNetworking.ShifterType.ARMORED;
                        } else if (hasBeastTag) {
                           shifterType = ModNetworking.ShifterType.BEAST;
                        } else if (hasWarhammerTag) {
                           shifterType = ModNetworking.ShifterType.WARHAMMER;
                        } else {
                           shifterType = ModNetworking.ShifterType.COLOSSAL;
                        }

                        if (!isBeastStaminaImmune(player.getUuid()) && shifterType != ModNetworking.ShifterType.CART) {
                           float shiftCost = shifterType == ModNetworking.ShifterType.COLOSSAL ? playerMax * 0.4F : playerMax * 0.2F;
                           playerStamina.put(player.getUuid(), stamina - shiftCost);
                        }

                        broadcastBiteAnimation(player);
                        pendingBites.put(player.getUuid(), new ModNetworking.PendingBite(21, shifterType, false));
                        if (shifterType == ModNetworking.ShifterType.COLOSSAL && !player.getCommandTags().contains("titan_stealth")) {
                           nukeFreezeAnchors.put(player.getUuid(), new Vec3d(player.getX(), player.getY(), player.getZ()));
                           player.setNoGravity(true);
                           player.setVelocity(Vec3d.ZERO);
                           player.fallDistance = 0.0F;
                        }

                        DannysAot.LOGGER.info("Player {} initiated {} titan shift (bite phase)", player.getName().getString(), shifterType);
                     }
                  }
               }
            }
         }
      }
   }

   private static void broadcastBiteAnimation(ServerPlayerEntity player) {
      PlayBitePayload payload = new PlayBitePayload(player.getId());
      ServerPlayNetworking.send(player, payload);

      for (ServerPlayerEntity nearby : PlayerLookup.tracking(player)) {
         if (nearby != player) {
            ServerPlayNetworking.send(nearby, payload);
         }
      }
   }

   private static void firePreshiftAndQueueSpawn(ServerPlayerEntity player, ModNetworking.ShifterType shifterType) {
      ServerWorld level = player.getServerWorld();
      boolean playColossalNuke = shifterType == ModNetworking.ShifterType.COLOSSAL && !player.getCommandTags().contains("titan_stealth");
      ServerPlayNetworking.send(player, new PreshiftStartPayload(playColossalNuke, false));
      Set<ServerPlayerEntity> nearbyPlayers = new HashSet<>(PlayerLookup.tracking(player));
      PreshiftEffectPayload effectPayload = new PreshiftEffectPayload(player.getId(), playColossalNuke);

      for (ServerPlayerEntity nearby : nearbyPlayers) {
         if (nearby != player) {
            ServerPlayNetworking.send(nearby, effectPayload);
         }
      }

      double px = player.getX();
      double py = player.getY() + 1.0;
      double pz = player.getZ();
      BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
      level.spawnParticles(bloodParticle, px, py, pz, 30, 0.3, 0.4, 0.3, 0.1);
      level.spawnParticles(bloodParticle, px, py + 0.5, pz, 20, 0.1, 0.1, 0.1, 0.15);
      pendingShifts.put(player.getUuid(), new ModNetworking.PendingShift(player.getX(), player.getY(), player.getZ(), player.getYaw(), 40, shifterType));
   }

   private static void fireChestBlast(ServerWorld level, ServerPlayerEntity caster, float charge) {
      double radius = lerp(4.0, 12.0, charge);
      double radiusSq = radius * radius;
      float damagePeak = 30.0F * charge;
      Vec3d origin = caster.getPos().add(0.0, caster.getHeight() * 0.5, 0.0);
      level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 10.0F, 0.9F);
      level.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, origin.x, origin.y, origin.z, 1, 0.0, 0.0, 0.0, 0.0);
      SoldierboyBlastShakePayload shake = new SoldierboyBlastShakePayload(origin.x, origin.y, origin.z);

      for (ServerPlayerEntity p : level.getServer().getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(p, shake);
      }

      DustParticleEffect yellow = new DustParticleEffect(new Vector3f(1.0F, 0.9F, 0.2F), 1.5F);
      int burstCount = 60 + (int)(charge * 120.0F);

      for (int i = 0; i < burstCount; i++) {
         double theta = level.random.nextDouble() * Math.PI * 2.0;
         double phi = Math.acos(2.0 * level.random.nextDouble() - 1.0);
         double sinPhi = Math.sin(phi);
         double dirX = sinPhi * Math.cos(theta);
         double dirY = Math.cos(phi);
         double dirZ = sinPhi * Math.sin(theta);
         double speed = 0.5 + level.random.nextDouble() * 0.6;
         level.spawnParticles(yellow, origin.x, origin.y, origin.z, 0, dirX, dirY, dirZ, speed);
      }

      Box aabb = new Box(origin.x - radius, origin.y - radius, origin.z - radius, origin.x + radius, origin.y + radius, origin.z + radius);

      for (Entity e : level.getOtherEntities(caster, aabb)) {
         if (e instanceof LivingEntity living && living != caster) {
            Vec3d toEntity = e.getPos().add(0.0, e.getHeight() * 0.5, 0.0).subtract(origin);
            double distSq = toEntity.lengthSquared();
            if (!(distSq > radiusSq)) {
               double dist = Math.sqrt(distSq);
               double linearFalloff = 1.0 - dist / radius;
               float dmg = damagePeak * (float)(0.4 + 0.6 * linearFalloff);
               PowerDamageMarker.APPLYING_ABILITY.set(Boolean.TRUE);

               try {
                  living.damage(level.getDamageSources().playerAttack(caster), dmg);
               } finally {
                  PowerDamageMarker.APPLYING_ABILITY.set(Boolean.FALSE);
               }

               living.setOnFireFor(5);
               Vec3d outward = toEntity.normalize();
               Vec3d var54 = outward.multiply(2.0 + 2.0 * linearFalloff).add(0.0, 1.0 + 0.6 * linearFalloff, 0.0);
               living.setVelocity(living.getVelocity().add(var54));
               living.velocityModified = true;
               if (living instanceof ServerPlayerEntity targetPlayer) {
                  BloodlineData targetData = BloodlineData.get(level);
                  BloodlineType targetBl = targetData.getBloodline(targetPlayer.getUuid());
                  if (targetBl == BloodlineType.HOMELANDER
                     || targetBl == BloodlineType.SOLDIERBOY
                     || targetBl == BloodlineType.ATRAIN
                     || targetBl == BloodlineType.TRANSLUCENT
                     || targetBl == BloodlineType.BUTCHER) {
                     int durationTicks = 600 + level.random.nextInt(601);
                     targetPlayer.addStatusEffect(new StatusEffectInstance(ModEffects.POWER_DISABLE, durationTicks, 0, false, false, true));
                     AtrainSpeedTracker.clear(targetPlayer);
                     TranslucentTracker.clear(targetPlayer);
                     updateHomelanderAbilities(targetPlayer, null);
                  }
               }
            }
         }
      }

      boolean griefing = DannysAot.isTitanGriefingEnabled(level);
      if (griefing) {
         int radiusCeil = (int)Math.ceil(radius) + 1;
         List<double[]> candidates = new ArrayList<>();

         for (int dx = -radiusCeil; dx <= radiusCeil; dx++) {
            for (int dy = -radiusCeil; dy <= radiusCeil; dy++) {
               for (int dz = -radiusCeil; dz <= radiusCeil; dz++) {
                  double cellDistSq = dx * dx + dy * dy + dz * dz;
                  if (!(cellDistSq > radiusSq)) {
                     BlockPos pos = BlockPos.ofFloored(origin.x + dx, origin.y + dy, origin.z + dz);
                     BlockState state = level.getBlockState(pos);
                     if (!state.isAir()
                        && !(state.getHardness(level, pos) < 0.0F)
                        && !state.isIn(BlockTags.WITHER_IMMUNE)
                        && !(state.getBlock() instanceof FluidBlock)
                        && !(state.getBlock() instanceof AbstractFireBlock)) {
                        candidates.add(new double[]{pos.asLong(), cellDistSq});
                     }
                  }
               }
            }
         }

         candidates.sort((a, b) -> Double.compare(a[1], b[1]));
         int limit = Math.min(candidates.size(), 4000);

         for (int i = 0; i < limit; i++) {
            BlockPos pos = BlockPos.fromLong((long)candidates.get(i)[0]);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
               Vec3d outward = Vec3d.ofCenter(pos).subtract(origin);
               if (outward.lengthSquared() < 1.0E-4) {
                  outward = new Vec3d(0.0, 1.0, 0.0);
               } else {
                  outward = outward.normalize();
               }

               Vec3d flingDir = outward.multiply(0.8).add(0.0, 0.5, 0.0);
               applyLaserBlockEffect(level, caster, pos, state, flingDir);
            }
         }
      }
   }

   private static void fireChestLaserTick(ServerWorld level, ServerPlayerEntity caster, Vec3d lookInput) {
      fireChestLaser(level, caster, 1.0F, lookInput);
   }

   private static void fireChestLaser(ServerWorld level, ServerPlayerEntity caster, float charge, Vec3d lookInput) {
      Vec3d horiz = new Vec3d(lookInput.x, 0.0, lookInput.z);
      if (horiz.lengthSquared() < 1.0E-4) {
         double yawRad = Math.toRadians(caster.getYaw());
         horiz = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
      }

      Vec3d dir = horiz.normalize();
      double range = lerp(80.0, 80.0, charge);
      double radius = lerp(2.5, 2.5, charge);
      float damage = 8.0F * charge;
      int blockCap = (int)(80.0F * (0.5F + 0.5F * charge));
      Vec3d origin = caster.getPos().add(0.0, caster.getHeight() * 0.5, 0.0);
      Vec3d endpoint = origin.add(dir.multiply(range));
      Box beamAabb = new Box(origin, endpoint).expand(radius + 0.5);

      for (Entity e : level.getOtherEntities(caster, beamAabb)) {
         if (e instanceof LivingEntity living && living != caster) {
            Vec3d toEntity = e.getPos().add(0.0, e.getHeight() * 0.5, 0.0).subtract(origin);
            double along = toEntity.dotProduct(dir);
            if (!(along < 0.0) && !(along > range)) {
               Vec3d perp = toEntity.subtract(dir.multiply(along));
               if (!(perp.length() > radius + 0.5)) {
                  boolean wasAlive = living.isAlive();
                  PowerDamageMarker.APPLYING_ABILITY.set(Boolean.TRUE);

                  try {
                     living.damage(level.getDamageSources().playerAttack(caster), damage);
                  } finally {
                     PowerDamageMarker.APPLYING_ABILITY.set(Boolean.FALSE);
                  }

                  living.setOnFireFor(5);
                  living.setVelocity(living.getVelocity().add(dir.multiply(1.0)));
                  living.velocityModified = true;
                  if (wasAlive && living.isDead()) {
                     GibEntity.spawnGibs(level, living, dir, 1.0F);
                  }
               }
            }
         }
      }

      Set<Long> visited = new HashSet<>();
      int flung = 0;
      double axialStep = 0.5;
      int radiusCeil = (int)Math.ceil(radius) + 1;

      for (double t = 0.0; t < range && flung < blockCap; t += axialStep) {
         Vec3d center = origin.add(dir.multiply(t));

         for (int dx = -radiusCeil; dx <= radiusCeil && flung < blockCap; dx++) {
            for (int dy = -radiusCeil; dy <= radiusCeil && flung < blockCap; dy++) {
               for (int dz = -radiusCeil; dz <= radiusCeil && flung < blockCap; dz++) {
                  double cellX = center.x + dx;
                  double cellY = center.y + dy;
                  double cellZ = center.z + dz;
                  Vec3d cellCenter = new Vec3d(Math.floor(cellX) + 0.5, Math.floor(cellY) + 0.5, Math.floor(cellZ) + 0.5);
                  Vec3d toCell = cellCenter.subtract(origin);
                  double along = toCell.dotProduct(dir);
                  if (!(along < 0.0) && !(along > range)) {
                     Vec3d perp = toCell.subtract(dir.multiply(along));
                     if (!(perp.length() > radius + 0.5)) {
                        BlockPos pos = BlockPos.ofFloored(cellCenter);
                        if (visited.add(pos.asLong())) {
                           BlockState state = level.getBlockState(pos);
                           if (!state.isAir()
                              && !(state.getHardness(level, pos) < 0.0F)
                              && !state.isIn(BlockTags.WITHER_IMMUNE)
                              && !(state.getBlock() instanceof FluidBlock)
                              && !(state.getBlock() instanceof AbstractFireBlock)) {
                              applyLaserBlockEffect(level, caster, pos, state, dir);
                              flung++;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }

   private static void applyLaserBlockEffect(ServerWorld level, ServerPlayerEntity caster, BlockPos pos, BlockState state, Vec3d dir) {
      boolean canGrief = DannysAot.isTitanGriefingEnabled(level);
      if (canGrief) {
         HomelanderFlightServerHandler.flingBlockAsLaser(level, caster, pos, state, dir);
         BlockState fireState = AbstractFireBlock.getState(level, pos);
         level.setBlockState(pos, fireState, 11);
         HomelanderFlightServerHandler.registerLaserFire(level, pos);
      } else {
         BlockPos firePos = pos.up();
         if (level.getBlockState(firePos).isAir()) {
            BlockState fireState = AbstractFireBlock.getState(level, firePos);
            if (!fireState.isAir()) {
               level.setBlockState(firePos, fireState, 11);
               HomelanderFlightServerHandler.registerLaserFire(level, firePos);
            }
         }
      }
   }

   private static void tickLaserFirerTimeouts(MinecraftServer server) {
      if (!activeLaserFirers.isEmpty()) {
         long now = server.getOverworld().getTime();
         Iterator<UUID> it = activeLaserFirers.iterator();

         while (it.hasNext()) {
            UUID id = it.next();
            Long last = lastLaserPayloadTick.get(id);
            if (last == null || now - last > 3L) {
               it.remove();
               lastLaserPayloadTick.remove(id);
               broadcastLaserActive(server, id, false);
            }
         }
      }
   }

   private static void broadcastLaserActive(MinecraftServer server, UUID firer, boolean active) {
      HomelanderLaserActivePayload payload = new HomelanderLaserActivePayload(firer, active);

      for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(p, payload);
      }
   }

   private static void tickPowerDisableSync(MinecraftServer server) {
      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         UUID id = player.getUuid();
         boolean disabled = ModEffects.isPowerDisabled(player);
         boolean wasDisabled = wasPowerDisabledLastTick.contains(id);
         if (disabled) {
            wasPowerDisabledLastTick.add(id);
         } else {
            wasPowerDisabledLastTick.remove(id);
         }

         if (disabled != wasDisabled) {
            BloodlineType bl = BloodlineData.get(player.getServerWorld()).getBloodline(id);
            if (bl != null) {
               if (disabled) {
                  if (bl == BloodlineType.HOMELANDER) {
                     updateHomelanderAbilities(player, null);
                     if (HomelanderFlightServerHandler.isFlying(id)) {
                        HomelanderFlightServerHandler.setFlying(player, false);
                        HomelanderFlyStatePayload offBroadcast = new HomelanderFlyStatePayload(id, false, false);

                        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                           ServerPlayNetworking.send(p, offBroadcast);
                        }
                     }
                  }

                  if (bl == BloodlineType.ATRAIN) {
                     AtrainSpeedTracker.clear(player);
                  }

                  if (bl == BloodlineType.TRANSLUCENT) {
                     TranslucentTracker.clear(player);
                  }
               } else {
                  updateHomelanderAbilities(player, bl);
               }
            }
         }
      }

      wasPowerDisabledLastTick.removeIf(idx -> server.getPlayerManager().getPlayer(idx) == null);
   }

   private static void tickPowerRegen(MinecraftServer server) {
      if (server.getTicks() % 20 == 0) {
         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            BloodlineType bl = BloodlineData.get(player.getServerWorld()).getBloodline(player.getUuid());
            if ((
                  bl == BloodlineType.HOMELANDER
                     || bl == BloodlineType.SOLDIERBOY
                     || bl == BloodlineType.ATRAIN
                     || bl == BloodlineType.TRANSLUCENT
                     || bl == BloodlineType.BUTCHER
               )
               && !ModEffects.isPowerDisabled(player)) {
               int amplifier = bl == BloodlineType.ATRAIN ? 1 : 0;
               StatusEffectInstance existing = player.getStatusEffect(StatusEffects.REGENERATION);
               if (existing == null || existing.getDuration() <= 100 || existing.getAmplifier() != amplifier) {
                  StatusEffectInstance regen = new StatusEffectInstance(StatusEffects.REGENERATION, 600, amplifier, false, false, false);
                  player.addStatusEffect(regen);
               }
            }
         }
      }
   }

   private static void tickChestLaserFirerTimeouts(MinecraftServer server) {
      if (!activeChestLaserFirers.isEmpty()) {
         long now = server.getOverworld().getTime();
         Iterator<UUID> it = activeChestLaserFirers.iterator();

         while (it.hasNext()) {
            UUID id = it.next();
            Long last = lastChestLaserTick.get(id);
            if (last == null || now - last > 3L) {
               it.remove();
               lastChestLaserTick.remove(id);
               SoldierboyLaserActivePayload off = new SoldierboyLaserActivePayload(id, false);

               for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                  ServerPlayNetworking.send(p, off);
               }

               ServerPlayerEntity firer = server.getPlayerManager().getPlayer(id);
               if (firer != null && !firer.isCreative() && !firer.isSpectator() && firer.getAbilities().allowFlying) {
                  firer.getAbilities().allowFlying = false;
                  firer.getAbilities().flying = false;
                  firer.sendAbilitiesUpdate();
               }
            }
         }
      }
   }

   private static void fireLaserBeam(ServerWorld level, ServerPlayerEntity caster, Vec3d origin, Vec3d dir, Set<UUID> alreadyHitThisTick) {
      Vec3d endpoint = origin.add(dir.multiply(80.0));
      Box beamAabb = new Box(origin, endpoint).expand(1.1);

      for (Entity e : level.getOtherEntities(caster, beamAabb)) {
         if (e instanceof LivingEntity living && living != caster && (alreadyHitThisTick == null || alreadyHitThisTick.add(living.getUuid()))) {
            Vec3d toEntity = e.getPos().add(0.0, e.getHeight() * 0.5, 0.0).subtract(origin);
            double along = toEntity.dotProduct(dir);
            if (!(along < 0.0) && !(along > 80.0)) {
               Vec3d perp = toEntity.subtract(dir.multiply(along));
               if (!(perp.length() > 1.1)) {
                  boolean wasAlive = living.isAlive();
                  PowerDamageMarker.APPLYING_ABILITY.set(Boolean.TRUE);

                  try {
                     living.damage(level.getDamageSources().playerAttack(caster), 12.0F);
                  } finally {
                     PowerDamageMarker.APPLYING_ABILITY.set(Boolean.FALSE);
                  }

                  living.setOnFireFor(5);
                  living.setVelocity(living.getVelocity().add(dir.multiply(0.4)));
                  living.velocityModified = true;
                  if (wasAlive && living.isDead()) {
                     GibEntity.spawnGibs(level, living, dir, 1.0F);
                  }
               }
            }
         }
      }

      Set<Long> visited = new HashSet<>();
      int flung = 0;

      for (double t = 0.0; t < 80.0 && flung < 200; t += 0.5) {
         Vec3d p = origin.add(dir.multiply(t));
         BlockPos pos = BlockPos.ofFloored(p);
         long packed = pos.asLong();
         if (visited.add(packed)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()
               && !(state.getHardness(level, pos) < 0.0F)
               && !state.isIn(BlockTags.WITHER_IMMUNE)
               && !(state.getBlock() instanceof FluidBlock)
               && !(state.getBlock() instanceof AbstractFireBlock)) {
               applyLaserBlockEffect(level, caster, pos, state, dir);
               flung++;
            }
         }
      }
   }

   private static void firePreshiftOnly(ServerPlayerEntity player, ModNetworking.ShifterType shifterType) {
      ServerWorld level = player.getServerWorld();
      boolean playColossalNuke = shifterType == ModNetworking.ShifterType.COLOSSAL && !player.getCommandTags().contains("titan_stealth");
      ServerPlayNetworking.send(player, new PreshiftStartPayload(playColossalNuke, true));
      Set<ServerPlayerEntity> nearbyPlayers = new HashSet<>(PlayerLookup.tracking(player));
      PreshiftEffectPayload effectPayload = new PreshiftEffectPayload(player.getId(), playColossalNuke);

      for (ServerPlayerEntity nearby : nearbyPlayers) {
         if (nearby != player) {
            ServerPlayNetworking.send(nearby, effectPayload);
         }
      }

      double px = player.getX();
      double py = player.getY() + 1.0;
      double pz = player.getZ();
      BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
      level.spawnParticles(bloodParticle, px, py, pz, 30, 0.3, 0.4, 0.3, 0.1);
      level.spawnParticles(bloodParticle, px, py + 0.5, pz, 20, 0.1, 0.1, 0.1, 0.15);
   }

   private static void handleTeaseShift(ServerPlayerEntity player, ServerWorld level) {
      if (!player.isSpectator()) {
         if (HandcuffsTracker.isCuffed(player.getUuid())) {
            player.sendMessage(Text.literal("Cannot transform while cuffed"), true);
         } else if (!(player.getVehicle() instanceof ColossalTitanEntity)
            && !(player.getVehicle() instanceof AttackTitanEntity)
            && !(player.getVehicle() instanceof ArmoredTitanEntity)
            && !(player.getVehicle() instanceof FemaleTitanEntity)
            && !(player.getVehicle() instanceof BeastTitanEntity)
            && !(player.getVehicle() instanceof WarhammerTitanEntity)
            && !(player.getVehicle() instanceof FemaleCrystalShellEntity)) {
            if (!pendingBites.containsKey(player.getUuid())) {
               if (!pendingShifts.containsKey(player.getUuid())) {
                  Identifier pathsDim = new Identifier("dannys-aot", "paths");
                  if (player.getWorld().getRegistryKey().getValue().equals(pathsDim)) {
                     player.sendMessage(Text.literal("Cannot transform in the Paths"), true);
                  } else {
                     if (player.hasStatusEffect(StatusEffects.WEAKNESS)) {
                        StatusEffectInstance weaknessEffect = player.getStatusEffect(StatusEffects.WEAKNESS);
                        if (weaknessEffect != null && weaknessEffect.getAmplifier() >= 4) {
                           player.sendMessage(Text.literal("Too exhausted to transform"), true);
                           return;
                        }
                     }

                     boolean hasColossalTag = PowerAuthority.hasShifter(player, TitanPowerType.COLOSSAL);
                     boolean hasAttackTag = PowerAuthority.hasShifter(player, TitanPowerType.ATTACK);
                     boolean hasArmoredTag = PowerAuthority.hasShifter(player, TitanPowerType.ARMORED);
                     boolean hasBeastTag = PowerAuthority.hasShifter(player, TitanPowerType.BEAST);
                     boolean hasFemaleTag = PowerAuthority.hasShifter(player, TitanPowerType.FEMALE);
                     boolean hasWarhammerTag = PowerAuthority.hasShifter(player, TitanPowerType.WARHAMMER);
                     boolean hasJawShifterTag = PowerAuthority.hasShifter(player, TitanPowerType.JAW);
                     boolean hasFounderTag = player.getCommandTags().contains("founder");
                     boolean hasTripleTTag = player.getCommandTags().contains("triple_t");
                     boolean hasOgreShifterTag = player.getCommandTags().contains("ogre_shifter");
                     boolean hasCartShifterTag = player.getCommandTags().contains("cart_shifter");
                     if (!hasColossalTag
                        && !hasAttackTag
                        && !hasArmoredTag
                        && !hasBeastTag
                        && !hasFemaleTag
                        && !hasWarhammerTag
                        && !hasFounderTag
                        && !hasTripleTTag
                        && !hasOgreShifterTag
                        && !hasJawShifterTag
                        && !hasCartShifterTag) {
                        playerStamina.remove(player.getUuid());
                        player.sendMessage(Text.literal("No shifter powers"), true);
                     } else {
                        float playerMax = getMaxStaminaForPlayer(player);
                        playerMaxStamina.put(player.getUuid(), playerMax);
                        float stamina = playerStamina.getOrDefault(player.getUuid(), playerMax);
                        if (stamina < playerMax * 0.25F) {
                           player.sendMessage(Text.literal("Not enough stamina to transform"), true);
                        } else {
                           ModNetworking.ShifterType shifterType;
                           if (hasJawShifterTag) {
                              shifterType = ModNetworking.ShifterType.JAW;
                           } else if (hasCartShifterTag) {
                              shifterType = ModNetworking.ShifterType.CART;
                           } else if (hasOgreShifterTag) {
                              shifterType = ModNetworking.ShifterType.OGRE_SHIFTER;
                           } else if (hasFounderTag) {
                              shifterType = ModNetworking.ShifterType.FOUNDING;
                           } else if (hasTripleTTag) {
                              shifterType = ModNetworking.ShifterType.TRIPLE_T;
                           } else if (hasAttackTag) {
                              shifterType = ModNetworking.ShifterType.ATTACK;
                           } else if (hasFemaleTag) {
                              shifterType = ModNetworking.ShifterType.FEMALE;
                           } else if (hasArmoredTag) {
                              shifterType = ModNetworking.ShifterType.ARMORED;
                           } else if (hasBeastTag) {
                              shifterType = ModNetworking.ShifterType.BEAST;
                           } else if (hasWarhammerTag) {
                              shifterType = ModNetworking.ShifterType.WARHAMMER;
                           } else {
                              shifterType = ModNetworking.ShifterType.COLOSSAL;
                           }

                           if (!isBeastStaminaImmune(player.getUuid()) && shifterType != ModNetworking.ShifterType.CART) {
                              float shiftCost = shifterType == ModNetworking.ShifterType.COLOSSAL ? playerMax * 0.2F : playerMax * 0.1F;
                              playerStamina.put(player.getUuid(), stamina - shiftCost);
                           }

                           broadcastBiteAnimation(player);
                           pendingBites.put(player.getUuid(), new ModNetworking.PendingBite(21, shifterType, true));
                           DannysAot.LOGGER.info("Player {} initiated {} tease shift (bite phase, no spawn)", player.getName().getString(), shifterType);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void tickColossalShiftFreeze(MinecraftServer server) {
      if (!nukeFreezeAnchors.isEmpty()) {
         Iterator<Entry<UUID, Vec3d>> it = nukeFreezeAnchors.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Vec3d> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null && player.isAlive() && !player.isRemoved()) {
               Vec3d anchor = entry.getValue();
               player.setVelocity(Vec3d.ZERO);
               player.fallDistance = 0.0F;
               player.setPosition(anchor.x, anchor.y, anchor.z);
            } else {
               it.remove();
            }
         }
      }
   }

   private static void clearColossalShiftFreeze(ServerPlayerEntity player) {
      if (nukeFreezeAnchors.remove(player.getUuid()) != null) {
         player.setNoGravity(false);
      }
   }

   private static void tickPendingBites(MinecraftServer server) {
      Iterator<Entry<UUID, ModNetworking.PendingBite>> iterator = pendingBites.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, ModNetworking.PendingBite> entry = iterator.next();
         UUID playerUUID = entry.getKey();
         ModNetworking.PendingBite bite = entry.getValue();
         ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUUID);
         if (player != null && !player.isRemoved() && player.isAlive()) {
            if (bite.ticksRemaining() <= 1) {
               if (bite.tease()) {
                  firePreshiftOnly(player, bite.shifterType());
               } else {
                  firePreshiftAndQueueSpawn(player, bite.shifterType());
               }

               iterator.remove();
            } else {
               pendingBites.put(playerUUID, new ModNetworking.PendingBite(bite.ticksRemaining() - 1, bite.shifterType(), bite.tease()));
            }
         } else {
            iterator.remove();
         }
      }
   }

   private static void tickPendingShifts(MinecraftServer server) {
      Iterator<Entry<UUID, ModNetworking.PendingShift>> iterator = pendingShifts.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, ModNetworking.PendingShift> entry = iterator.next();
         UUID playerUUID = entry.getKey();
         ModNetworking.PendingShift shift = entry.getValue();
         ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUUID);
         if (player != null && !player.isRemoved() && player.isAlive()) {
            if (shift.ticksRemaining() <= 1) {
               double currentX = player.getX();
               double currentY = player.getY();
               double currentZ = player.getZ();
               if (shift.shifterType() == ModNetworking.ShifterType.JAW) {
                  spawnTestShifterTitan(player, player.getServerWorld(), currentX, currentY, currentZ, player.getYaw());
               } else if (shift.shifterType() == ModNetworking.ShifterType.CART) {
                  spawnCartShifterTitan(player, player.getServerWorld(), currentX, currentY, currentZ, player.getYaw());
               } else if (shift.shifterType() == ModNetworking.ShifterType.OGRE_SHIFTER) {
                  spawnOgreShifterTitan(player, player.getServerWorld(), currentX, currentY, currentZ, player.getYaw());
               } else if (shift.shifterType() == ModNetworking.ShifterType.FOUNDING) {
                  spawnFoundingTitan(player, player.getServerWorld(), currentX, currentY, currentZ, player.getYaw());
               } else if (shift.shifterType() == ModNetworking.ShifterType.TRIPLE_T) {
                  spawnTripleTTitan(player, player.getServerWorld(), currentX, currentY, currentZ, player.getYaw());
               } else if (shift.shifterType() == ModNetworking.ShifterType.ATTACK) {
                  spawnAttackTitan(player, player.getServerWorld(), currentX, currentY, currentZ, player.getYaw());
               } else if (shift.shifterType() == ModNetworking.ShifterType.FEMALE) {
                  spawnFemaleTitan(player, player.getServerWorld(), currentX, currentY, currentZ, player.getYaw());
               } else if (shift.shifterType() == ModNetworking.ShifterType.ARMORED) {
                  spawnArmoredTitan(player, player.getServerWorld(), currentX, currentY, currentZ, player.getYaw());
               } else if (shift.shifterType() == ModNetworking.ShifterType.BEAST) {
                  spawnBeastTitan(player, player.getServerWorld(), currentX, currentY, currentZ, player.getYaw());
               } else if (shift.shifterType() == ModNetworking.ShifterType.WARHAMMER) {
                  spawnWarhammerTitan(player, player.getServerWorld(), currentX, currentY, currentZ, player.getYaw());
               } else if (shift.shifterType() == ModNetworking.ShifterType.PURE_TITAN) {
                  spawnPureTitan(player, player.getServerWorld(), currentX, currentY, currentZ, player.getYaw());
               } else {
                  spawnColossalTitan(player, player.getServerWorld(), currentX, currentY, currentZ, player.getYaw());
               }

               iterator.remove();
            } else {
               pendingShifts.put(
                  playerUUID, new ModNetworking.PendingShift(shift.x(), shift.y(), shift.z(), shift.yaw(), shift.ticksRemaining() - 1, shift.shifterType())
               );
            }
         } else {
            iterator.remove();
         }
      }
   }

   public static void spawnShiftLightning(ServerWorld level, double x, double y, double z) {
      LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(level);
      if (lightning != null) {
         lightning.refreshPositionAfterTeleport(x, y, z);
         lightning.setCosmetic(true);
         level.spawnEntity(lightning);
         ShiftLightningPayload payload = new ShiftLightningPayload(lightning.getId());

         for (ServerPlayerEntity recipient : PlayerLookup.tracking(level, new BlockPos((int)x, (int)y, (int)z))) {
            ServerPlayNetworking.send(recipient, payload);
         }
      }
   }

   private static void spawnColossalTitan(ServerPlayerEntity player, ServerWorld level, double x, double y, double z, float yaw) {
      if (!player.isRemoved() && player.isAlive()) {
         if (!(player.getVehicle() instanceof ColossalTitanEntity)) {
            killEatingTitan(player);
            spawnShiftLightning(level, x, y, z);
            ColossalTitanEntity titan = new ColossalTitanEntity(DannysAot.COLOSSAL_TITAN, level);
            titan.setPosition(x, y, z);
            titan.setYaw(yaw);
            titan.bodyYaw = yaw;
            titan.setHeadYaw(yaw);
            titan.setTransformationTicks(95);
            titan.setNoGravity(true);
            level.spawnEntity(titan);
            titan.spawnHitboxes();
            triggerTransformationExplosion(level, x, y, z, player, 20.0, 30.0F, 3.0);
            Set<ServerPlayerEntity> recipients = new HashSet<>(PlayerLookup.tracking(level, new BlockPos((int)x, (int)y, (int)z)));
            recipients.add(player);
            int colossalSpawnType = player.getCommandTags().contains("titan_stealth") ? 0 : 6;
            TitanSpawnPayload spawnPayload = new TitanSpawnPayload(x, y, z, titan.getId(), colossalSpawnType);

            for (ServerPlayerEntity recipient : recipients) {
               ServerPlayNetworking.send(recipient, spawnPayload);
            }

            clearColossalShiftFreeze(player);
            player.setInvisible(true);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 155, 4, false, false));
            ascendingPlayers.put(player.getUuid(), new ModNetworking.AscendingPlayer(titan, 0, ModNetworking.ShifterType.COLOSSAL));
            ModCriteriaTriggers.COLOSSAL_TITAN_SHIFT.trigger(player);
            DannysAot.LOGGER.info("Player {} shifted into Colossal Titan at ({}, {}, {})", new Object[]{player.getName().getString(), x, y, z});
         }
      }
   }

   private static void spawnFoundingTitan(ServerPlayerEntity player, ServerWorld level, double x, double y, double z, float yaw) {
      if (!player.isRemoved() && player.isAlive()) {
         if (!(player.getVehicle() instanceof FoundingTitanEntity)) {
            killEatingTitan(player);
            spawnShiftLightning(level, x, y, z);
            FoundingTitanEntity titan = new FoundingTitanEntity(DannysAot.FOUNDING_TITAN, level);
            titan.setPosition(x, y, z);
            titan.setYaw(yaw);
            titan.bodyYaw = yaw;
            titan.setHeadYaw(yaw);
            titan.setTransformationTicks(80);
            titan.setNoGravity(true);
            level.spawnEntity(titan);
            titan.spawnHitboxes();
            triggerTransformationExplosion(level, x, y, z, player, 10.0, 20.0F, 2.0);
            Set<ServerPlayerEntity> recipients = new HashSet<>(PlayerLookup.tracking(level, new BlockPos((int)x, (int)y, (int)z)));
            recipients.add(player);
            TitanSpawnPayload spawnPayload = new TitanSpawnPayload(x, y, z, titan.getId(), 1);

            for (ServerPlayerEntity recipient : recipients) {
               ServerPlayNetworking.send(recipient, spawnPayload);
            }

            player.setInvisible(true);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false));
            ascendingPlayers.put(player.getUuid(), new ModNetworking.AscendingPlayer(titan, 0, ModNetworking.ShifterType.FOUNDING));
            DannysAot.LOGGER.info("Player {} shifted into Founding Titan at ({}, {}, {})", new Object[]{player.getName().getString(), x, y, z});
         }
      }
   }

   private static void spawnAttackTitan(ServerPlayerEntity player, ServerWorld level, double x, double y, double z, float yaw) {
      if (!player.isRemoved() && player.isAlive()) {
         if (!(player.getVehicle() instanceof AttackTitanEntity)) {
            killEatingTitan(player);
            spawnShiftLightning(level, x, y, z);
            AttackTitanEntity titan = new AttackTitanEntity(DannysAot.ATTACK_TITAN, level);
            titan.setPosition(x, y, z);
            titan.setYaw(yaw);
            titan.bodyYaw = yaw;
            titan.setHeadYaw(yaw);
            titan.setTransformationTicks(80);
            titan.setNoGravity(true);
            level.spawnEntity(titan);
            titan.spawnHitboxes();
            triggerTransformationExplosion(level, x, y, z, player, 10.0, 20.0F, 2.0);
            Set<ServerPlayerEntity> recipients = new HashSet<>(PlayerLookup.tracking(level, new BlockPos((int)x, (int)y, (int)z)));
            recipients.add(player);
            TitanSpawnPayload spawnPayload = new TitanSpawnPayload(x, y, z, titan.getId(), 1);

            for (ServerPlayerEntity recipient : recipients) {
               ServerPlayNetworking.send(recipient, spawnPayload);
            }

            player.setInvisible(true);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false));
            ascendingPlayers.put(player.getUuid(), new ModNetworking.AscendingPlayer(titan, 0, ModNetworking.ShifterType.ATTACK));
            ModCriteriaTriggers.ATTACK_TITAN_SHIFT.trigger(player);
            DannysAot.LOGGER.info("Player {} shifted into Attack Titan at ({}, {}, {})", new Object[]{player.getName().getString(), x, y, z});
         }
      }
   }

   private static void spawnTripleTTitan(ServerPlayerEntity player, ServerWorld level, double x, double y, double z, float yaw) {
      if (!player.isRemoved() && player.isAlive()) {
         if (!(player.getVehicle() instanceof TripleTTitanEntity)) {
            killEatingTitan(player);
            spawnShiftLightning(level, x, y, z);
            TripleTTitanEntity titan = new TripleTTitanEntity(DannysAot.TRIPLE_T_TITAN, level);
            titan.setPosition(x, y, z);
            titan.setYaw(yaw);
            titan.bodyYaw = yaw;
            titan.setHeadYaw(yaw);
            titan.setTransformationTicks(80);
            titan.setNoGravity(true);
            level.spawnEntity(titan);
            titan.spawnHitboxes();
            triggerTransformationExplosion(level, x, y, z, player, 10.0, 20.0F, 2.0);
            Set<ServerPlayerEntity> recipients = new HashSet<>(PlayerLookup.tracking(level, new BlockPos((int)x, (int)y, (int)z)));
            recipients.add(player);
            TitanSpawnPayload spawnPayload = new TitanSpawnPayload(x, y, z, titan.getId(), 1);

            for (ServerPlayerEntity recipient : recipients) {
               ServerPlayNetworking.send(recipient, spawnPayload);
            }

            player.setInvisible(true);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false));
            ascendingPlayers.put(player.getUuid(), new ModNetworking.AscendingPlayer(titan, 0, ModNetworking.ShifterType.TRIPLE_T));
            DannysAot.LOGGER.info("Player {} shifted into Triple T Titan at ({}, {}, {})", new Object[]{player.getName().getString(), x, y, z});
         }
      }
   }

   private static void spawnTestShifterTitan(ServerPlayerEntity player, ServerWorld level, double x, double y, double z, float yaw) {
      if (!player.isRemoved() && player.isAlive()) {
         if (!(player.getVehicle() instanceof TestShifterTitanEntity)) {
            killEatingTitan(player);
            spawnShiftLightning(level, x, y, z);
            TestShifterTitanEntity titan = new TestShifterTitanEntity(DannysAot.TEST_SHIFTER_TITAN, level);
            titan.setPosition(x, y, z);
            titan.setYaw(yaw);
            titan.bodyYaw = yaw;
            titan.setHeadYaw(yaw);
            titan.setTransformationTicks(80);
            titan.setNoGravity(true);
            level.spawnEntity(titan);
            titan.spawnHitboxes();
            triggerTransformationExplosion(level, x, y, z, player, 6.0, 10.0F, 1.0);
            Set<ServerPlayerEntity> recipients = new HashSet<>(PlayerLookup.tracking(level, new BlockPos((int)x, (int)y, (int)z)));
            recipients.add(player);
            TitanSpawnPayload spawnPayload = new TitanSpawnPayload(x, y, z, titan.getId(), 0);

            for (ServerPlayerEntity recipient : recipients) {
               ServerPlayNetworking.send(recipient, spawnPayload);
            }

            player.setInvisible(true);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false));
            ascendingPlayers.put(player.getUuid(), new ModNetworking.AscendingPlayer(titan, 0, ModNetworking.ShifterType.JAW));
            ModCriteriaTriggers.JAW_TITAN_SHIFT.trigger(player);
            DannysAot.LOGGER.info("Player {} shifted into Test Shifter Titan at ({}, {}, {})", new Object[]{player.getName().getString(), x, y, z});
         }
      }
   }

   private static void spawnCartShifterTitan(ServerPlayerEntity player, ServerWorld level, double x, double y, double z, float yaw) {
      if (!player.isRemoved() && player.isAlive()) {
         if (!(player.getVehicle() instanceof CartShifterTitanEntity)) {
            killEatingTitan(player);
            spawnShiftLightning(level, x, y, z);
            CartShifterTitanEntity titan = new CartShifterTitanEntity(DannysAot.CART_SHIFTER_TITAN, level);
            titan.setPosition(x, y, z);
            titan.setYaw(yaw);
            titan.bodyYaw = yaw;
            titan.setHeadYaw(yaw);
            titan.setTransformationTicks(80);
            titan.setNoGravity(true);
            level.spawnEntity(titan);
            titan.spawnHitboxes();
            triggerTransformationExplosion(level, x, y, z, player, 6.0, 10.0F, 1.0);
            Set<ServerPlayerEntity> recipients = new HashSet<>(PlayerLookup.tracking(level, new BlockPos((int)x, (int)y, (int)z)));
            recipients.add(player);
            TitanSpawnPayload spawnPayload = new TitanSpawnPayload(x, y, z, titan.getId(), 0);

            for (ServerPlayerEntity recipient : recipients) {
               ServerPlayNetworking.send(recipient, spawnPayload);
            }

            player.setInvisible(true);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false));
            ascendingPlayers.put(player.getUuid(), new ModNetworking.AscendingPlayer(titan, 0, ModNetworking.ShifterType.CART));
            ModCriteriaTriggers.CART_TITAN_SHIFT.trigger(player);
            DannysAot.LOGGER.info("Player {} shifted into Cart Shifter Titan at ({}, {}, {})", new Object[]{player.getName().getString(), x, y, z});
         }
      }
   }

   private static void spawnOgreShifterTitan(ServerPlayerEntity player, ServerWorld level, double x, double y, double z, float yaw) {
      if (!player.isRemoved() && player.isAlive()) {
         if (!(player.getVehicle() instanceof OgreShifterTitanEntity)) {
            killEatingTitan(player);
            spawnShiftLightning(level, x, y, z);
            OgreShifterTitanEntity titan = new OgreShifterTitanEntity(DannysAot.OGRE_SHIFTER_TITAN, level);
            titan.setPosition(x, y, z);
            titan.setYaw(yaw);
            titan.bodyYaw = yaw;
            titan.setHeadYaw(yaw);
            titan.setTransformationTicks(80);
            titan.setNoGravity(true);
            level.spawnEntity(titan);
            titan.spawnHitboxes();
            triggerTransformationExplosion(level, x, y, z, player, 10.0, 20.0F, 2.0);
            Set<ServerPlayerEntity> recipients = new HashSet<>(PlayerLookup.tracking(level, new BlockPos((int)x, (int)y, (int)z)));
            recipients.add(player);
            TitanSpawnPayload spawnPayload = new TitanSpawnPayload(x, y, z, titan.getId(), 1);

            for (ServerPlayerEntity recipient : recipients) {
               ServerPlayNetworking.send(recipient, spawnPayload);
            }

            player.setInvisible(true);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false));
            ascendingPlayers.put(player.getUuid(), new ModNetworking.AscendingPlayer(titan, 0, ModNetworking.ShifterType.OGRE_SHIFTER));
            DannysAot.LOGGER.info("Player {} shifted into Ogre Shifter Titan at ({}, {}, {})", new Object[]{player.getName().getString(), x, y, z});
         }
      }
   }

   private static void spawnArmoredTitan(ServerPlayerEntity player, ServerWorld level, double x, double y, double z, float yaw) {
      if (!player.isRemoved() && player.isAlive()) {
         if (!(player.getVehicle() instanceof ArmoredTitanEntity)) {
            killEatingTitan(player);
            spawnShiftLightning(level, x, y, z);
            ArmoredTitanEntity titan = new ArmoredTitanEntity(DannysAot.ARMORED_TITAN, level);
            titan.setPosition(x, y, z);
            titan.setYaw(yaw);
            titan.bodyYaw = yaw;
            titan.setHeadYaw(yaw);
            titan.setTransformationTicks(80);
            titan.setNoGravity(true);
            level.spawnEntity(titan);
            titan.spawnHitboxes();
            triggerTransformationExplosion(level, x, y, z, player, 10.0, 20.0F, 2.0);
            Set<ServerPlayerEntity> recipients = new HashSet<>(PlayerLookup.tracking(level, new BlockPos((int)x, (int)y, (int)z)));
            recipients.add(player);
            TitanSpawnPayload spawnPayload = new TitanSpawnPayload(x, y, z, titan.getId(), 2);

            for (ServerPlayerEntity recipient : recipients) {
               ServerPlayNetworking.send(recipient, spawnPayload);
            }

            player.setInvisible(true);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false));
            ascendingPlayers.put(player.getUuid(), new ModNetworking.AscendingPlayer(titan, 0, ModNetworking.ShifterType.ARMORED));
            ModCriteriaTriggers.ARMORED_TITAN_SHIFT.trigger(player);
            DannysAot.LOGGER.info("Player {} shifted into Armored Titan at ({}, {}, {})", new Object[]{player.getName().getString(), x, y, z});
         }
      }
   }

   private static void spawnFemaleTitan(ServerPlayerEntity player, ServerWorld level, double x, double y, double z, float yaw) {
      if (!player.isRemoved() && player.isAlive()) {
         if (!(player.getVehicle() instanceof FemaleTitanEntity)) {
            killEatingTitan(player);
            spawnShiftLightning(level, x, y, z);
            FemaleTitanEntity titan = new FemaleTitanEntity(DannysAot.FEMALE_TITAN, level);
            titan.setPosition(x, y, z);
            titan.setYaw(yaw);
            titan.bodyYaw = yaw;
            titan.setHeadYaw(yaw);
            titan.setTransformationTicks(80);
            titan.setNoGravity(true);
            level.spawnEntity(titan);
            titan.spawnHitboxes();
            triggerTransformationExplosion(level, x, y, z, player, 10.0, 20.0F, 2.0);
            Set<ServerPlayerEntity> recipients = new HashSet<>(PlayerLookup.tracking(level, new BlockPos((int)x, (int)y, (int)z)));
            recipients.add(player);
            TitanSpawnPayload spawnPayload = new TitanSpawnPayload(x, y, z, titan.getId(), 3);

            for (ServerPlayerEntity recipient : recipients) {
               ServerPlayNetworking.send(recipient, spawnPayload);
            }

            player.setInvisible(true);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false));
            ascendingPlayers.put(player.getUuid(), new ModNetworking.AscendingPlayer(titan, 0, ModNetworking.ShifterType.FEMALE));
            ModCriteriaTriggers.FEMALE_TITAN_SHIFT.trigger(player);
            DannysAot.LOGGER.info("Player {} shifted into Female Titan at ({}, {}, {})", new Object[]{player.getName().getString(), x, y, z});
         }
      }
   }

   private static void spawnBeastTitan(ServerPlayerEntity player, ServerWorld level, double x, double y, double z, float yaw) {
      if (!player.isRemoved() && player.isAlive()) {
         if (!(player.getVehicle() instanceof BeastTitanEntity)) {
            killEatingTitan(player);
            spawnShiftLightning(level, x, y, z);
            BeastTitanEntity titan = new BeastTitanEntity(DannysAot.BEAST_TITAN, level);
            titan.setPosition(x, y, z);
            titan.setYaw(yaw);
            titan.bodyYaw = yaw;
            titan.setHeadYaw(yaw);
            titan.setTransformationTicks(80);
            titan.setNoGravity(true);
            level.spawnEntity(titan);
            titan.spawnHitboxes();
            triggerTransformationExplosion(level, x, y, z, player, 10.0, 20.0F, 2.0);
            Set<ServerPlayerEntity> recipients = new HashSet<>(PlayerLookup.tracking(level, new BlockPos((int)x, (int)y, (int)z)));
            recipients.add(player);
            TitanSpawnPayload spawnPayload = new TitanSpawnPayload(x, y, z, titan.getId(), 4);

            for (ServerPlayerEntity recipient : recipients) {
               ServerPlayNetworking.send(recipient, spawnPayload);
            }

            player.setInvisible(true);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false));
            ascendingPlayers.put(player.getUuid(), new ModNetworking.AscendingPlayer(titan, 0, ModNetworking.ShifterType.BEAST));
            ModCriteriaTriggers.BEAST_TITAN_SHIFT.trigger(player);
            if (BloodlineData.get(player.getServerWorld()).getBloodline(player.getUuid()) == BloodlineType.ROYAL) {
               ModCriteriaTriggers.BEAST_TITAN_SHIFT_ROYAL.trigger(player);
            }

            DannysAot.LOGGER.info("Player {} shifted into Beast Titan at ({}, {}, {})", new Object[]{player.getName().getString(), x, y, z});
         }
      }
   }

   private static void spawnWarhammerTitan(ServerPlayerEntity player, ServerWorld level, double x, double y, double z, float yaw) {
      if (!player.isRemoved() && player.isAlive()) {
         if (!(player.getVehicle() instanceof WarhammerTitanEntity)) {
            killEatingTitan(player);
            spawnShiftLightning(level, x, y, z);
            WarhammerTitanEntity titan = new WarhammerTitanEntity(DannysAot.WARHAMMER_TITAN, level);
            titan.setPosition(x, y, z);
            titan.setYaw(yaw);
            titan.bodyYaw = yaw;
            titan.setHeadYaw(yaw);
            titan.setTransformationTicks(80);
            titan.setNoGravity(true);
            level.spawnEntity(titan);
            titan.spawnHitboxes();
            triggerTransformationExplosion(level, x, y, z, player, 8.0, 18.0F, 1.8);
            titan.spawnCrystalShell(x, y, z, player.getUuid());
            Set<ServerPlayerEntity> recipients = new HashSet<>(PlayerLookup.tracking(level, new BlockPos((int)x, (int)y, (int)z)));
            recipients.add(player);
            TitanSpawnPayload spawnPayload = new TitanSpawnPayload(x, y, z, titan.getId(), 5);

            for (ServerPlayerEntity recipient : recipients) {
               ServerPlayNetworking.send(recipient, spawnPayload);
            }

            player.setInvisible(true);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false));
            ascendingPlayers.put(player.getUuid(), new ModNetworking.AscendingPlayer(titan, 0, ModNetworking.ShifterType.WARHAMMER));
            DannysAot.LOGGER.info("Player {} shifted into Warhammer Titan at ({}, {}, {})", new Object[]{player.getName().getString(), x, y, z});
         }
      }
   }

   private static void spawnPureTitan(ServerPlayerEntity player, ServerWorld level, double x, double y, double z, float yaw) {
      if (!player.isRemoved() && player.isAlive()) {
         killEatingTitan(player);
         spawnShiftLightning(level, x, y, z);
         UUID tropicalPlayerUUID = UUID.fromString("5ad50394-dafd-4d11-b27c-e0aef6de7030");
         int titanType;
         if (player.getUuid().equals(tropicalPlayerUUID)) {
            titanType = 14;
         } else {
            titanType = level.random.nextInt(24);
         }

         LivingEntity titan;
         String titanLogName;
         if (titanType <= 1) {
            SmallTitanEntity t = new SmallTitanEntity(DannysAot.SMALL_TITAN, level);
            t.setPosition(x, y, z);
            t.setYaw(yaw);
            t.bodyYaw = yaw;
            t.setHeadYaw(yaw);
            t.addSelfInjectionPassenger(player);
            t.initialize(level, level.getLocalDifficulty(player.getBlockPos()), SpawnReason.CONVERSION, null, null);
            level.spawnEntity(t);
            titan = t;
            titanLogName = "Small Titan";
         } else if (titanType <= 3) {
            SmallTitan2Entity t = new SmallTitan2Entity(DannysAot.SMALL_TITAN_2, level);
            t.setPosition(x, y, z);
            t.setYaw(yaw);
            t.bodyYaw = yaw;
            t.setHeadYaw(yaw);
            t.addSelfInjectionPassenger(player);
            t.initialize(level, level.getLocalDifficulty(player.getBlockPos()), SpawnReason.CONVERSION, null, null);
            level.spawnEntity(t);
            titan = t;
            titanLogName = "Small Titan 2";
         } else if (titanType <= 5) {
            FritzTitanEntity t = new FritzTitanEntity(DannysAot.FRITZ_TITAN, level);
            t.setPosition(x, y, z);
            t.setYaw(yaw);
            t.bodyYaw = yaw;
            t.setHeadYaw(yaw);
            t.addSelfInjectionPassenger(player);
            t.initialize(level, level.getLocalDifficulty(player.getBlockPos()), SpawnReason.CONVERSION, null, null);
            level.spawnEntity(t);
            titan = t;
            titanLogName = "Fritz Titan";
         } else if (titanType <= 7) {
            TitanBeardEntity t = new TitanBeardEntity(DannysAot.TITAN_BEARD, level);
            t.setPosition(x, y, z);
            t.setYaw(yaw);
            t.bodyYaw = yaw;
            t.setHeadYaw(yaw);
            t.addSelfInjectionPassenger(player);
            t.initialize(level, level.getLocalDifficulty(player.getBlockPos()), SpawnReason.CONVERSION, null, null);
            level.spawnEntity(t);
            titan = t;
            titanLogName = "Titan Beard";
         } else if (titanType <= 9) {
            AbnormalTitanEntity t = new AbnormalTitanEntity(DannysAot.ABNORMAL_TITAN, level);
            t.setPosition(x, y, z);
            t.setYaw(yaw);
            t.bodyYaw = yaw;
            t.setHeadYaw(yaw);
            t.addSelfInjectionPassenger(player);
            t.initialize(level, level.getLocalDifficulty(player.getBlockPos()), SpawnReason.CONVERSION, null, null);
            level.spawnEntity(t);
            titan = t;
            titanLogName = "Abnormal Titan";
         } else if (titanType <= 11) {
            ConnieFatherEntity t = new ConnieFatherEntity(DannysAot.CONNIE_FATHER, level);
            t.setPosition(x, y, z);
            t.setYaw(yaw);
            t.bodyYaw = yaw;
            t.setHeadYaw(yaw);
            t.addSelfInjectionPassenger(player);
            t.initialize(level, level.getLocalDifficulty(player.getBlockPos()), SpawnReason.CONVERSION, null, null);
            level.spawnEntity(t);
            titan = t;
            titanLogName = "Connie's Father";
         } else if (titanType <= 13) {
            TitanEntity t = new TitanEntity(DannysAot.TITAN, level);
            t.setPosition(x, y, z);
            t.setYaw(yaw);
            t.bodyYaw = yaw;
            t.setHeadYaw(yaw);
            t.addSelfInjectionPassenger(player);
            t.initialize(level, level.getLocalDifficulty(player.getBlockPos()), SpawnReason.CONVERSION, null, null);
            level.spawnEntity(t);
            titan = t;
            titanLogName = "Pure Titan";
         } else if (titanType <= 15) {
            TitanTropicalEntity t = new TitanTropicalEntity(DannysAot.TITAN_TROPICAL, level);
            t.setPosition(x, y, z);
            t.setYaw(yaw);
            t.bodyYaw = yaw;
            t.setHeadYaw(yaw);
            t.addSelfInjectionPassenger(player);
            t.initialize(level, level.getLocalDifficulty(player.getBlockPos()), SpawnReason.CONVERSION, null, null);
            level.spawnEntity(t);
            titan = t;
            titanLogName = "Tropical Titan";
         } else if (titanType <= 17) {
            YellowTitanEntity t = new YellowTitanEntity(DannysAot.YELLOW_TITAN, level);
            t.setPosition(x, y, z);
            t.setYaw(yaw);
            t.bodyYaw = yaw;
            t.setHeadYaw(yaw);
            t.addSelfInjectionPassenger(player);
            t.initialize(level, level.getLocalDifficulty(player.getBlockPos()), SpawnReason.CONVERSION, null, null);
            level.spawnEntity(t);
            titan = t;
            titanLogName = "Yellow Titan";
         } else if (titanType <= 19) {
            SadTitanEntity t = new SadTitanEntity(DannysAot.SAD_TITAN, level);
            t.setPosition(x, y, z);
            t.setYaw(yaw);
            t.bodyYaw = yaw;
            t.setHeadYaw(yaw);
            t.addSelfInjectionPassenger(player);
            t.initialize(level, level.getLocalDifficulty(player.getBlockPos()), SpawnReason.CONVERSION, null, null);
            level.spawnEntity(t);
            titan = t;
            titanLogName = "Sad Titan";
         } else if (titanType <= 21) {
            CrawlerTitanEntity t = new CrawlerTitanEntity(DannysAot.CRAWLER_TITAN, level);
            t.setPosition(x, y, z);
            t.setYaw(yaw);
            t.bodyYaw = yaw;
            t.setHeadYaw(yaw);
            t.addSelfInjectionPassenger(player);
            t.initialize(level, level.getLocalDifficulty(player.getBlockPos()), SpawnReason.CONVERSION, null, null);
            level.spawnEntity(t);
            titan = t;
            titanLogName = "Crawler Titan";
         } else if (titanType == 22) {
            OgreTitanEntity t = new OgreTitanEntity(DannysAot.OGRE_TITAN, level);
            t.setPosition(x, y, z);
            t.setYaw(yaw);
            t.bodyYaw = yaw;
            t.setHeadYaw(yaw);
            t.addSelfInjectionPassenger(player);
            t.initialize(level, level.getLocalDifficulty(player.getBlockPos()), SpawnReason.CONVERSION, null, null);
            level.spawnEntity(t);
            titan = t;
            titanLogName = "Ogre Titan";
         } else {
            CrawlingAbnormalTitanEntity t = new CrawlingAbnormalTitanEntity(DannysAot.CRAWLING_ABNORMAL_TITAN, level);
            t.setPosition(x, y, z);
            t.setYaw(yaw);
            t.bodyYaw = yaw;
            t.setHeadYaw(yaw);
            t.addSelfInjectionPassenger(player);
            t.initialize(level, level.getLocalDifficulty(player.getBlockPos()), SpawnReason.CONVERSION, null, null);
            level.spawnEntity(t);
            titan = t;
            titanLogName = "Crawling Abnormal Titan";
         }

         DannysAot.LOGGER.info("Player {} turned into {} via self-injection", player.getName().getString(), titanLogName);
         String ownerName = pureTitanOwnerNames.remove(player.getUuid());
         if (ownerName != null) {
            titan.addCommandTag("dannysaot_roar_owner:" + ownerName);
         }

         Set<ServerPlayerEntity> recipients = new HashSet<>(PlayerLookup.tracking(level, new BlockPos((int)x, (int)y, (int)z)));
         recipients.add(player);
         TitanSpawnPayload spawnPayload = new TitanSpawnPayload(x, y, z, titan.getId(), 0);

         for (ServerPlayerEntity recipient : recipients) {
            ServerPlayNetworking.send(recipient, spawnPayload);
         }

         player.setInvisible(true);
         player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false));
         ascendingPlayers.put(player.getUuid(), new ModNetworking.AscendingPlayer(titan, 0, ModNetworking.ShifterType.PURE_TITAN));
      }
   }

   private static Vec3d getPureTitanRidePosition(LivingEntity titan) {
      double height = titan.getHeight() * 0.7;
      return new Vec3d(titan.getX(), titan.getY() + height, titan.getZ());
   }

   private static void killEatingTitan(ServerPlayerEntity player) {
      Entity vehicle = player.getVehicle();
      if (vehicle instanceof SmallTitanEntity smallTitan) {
         player.stopRiding();
         player.setInvisible(false);
         smallTitan.kill();
         DannysAot.LOGGER.info("Killed SmallTitan that was eating {} during transformation", player.getName().getString());
      } else if (vehicle instanceof SmallTitan2Entity smallTitan2) {
         player.stopRiding();
         player.setInvisible(false);
         smallTitan2.kill();
         DannysAot.LOGGER.info("Killed SmallTitan2 that was eating {} during transformation", player.getName().getString());
      } else if (vehicle instanceof FritzTitanEntity fritzTitan) {
         player.stopRiding();
         player.setInvisible(false);
         fritzTitan.kill();
         DannysAot.LOGGER.info("Killed FritzTitan that was eating {} during transformation", player.getName().getString());
      } else if (vehicle instanceof TitanEntity titan) {
         player.stopRiding();
         player.setInvisible(false);
         titan.kill();
         DannysAot.LOGGER.info("Killed TitanEntity that was eating {} during transformation", player.getName().getString());
      } else {
         Box searchBox = player.getBoundingBox().expand(50.0);
         ServerWorld level = player.getServerWorld();

         for (TitanEntity titan : level.getNonSpectatingEntities(TitanEntity.class, searchBox)) {
            if (titan.isEating() && titan.getEatingTargetId() == player.getId()) {
               titan.kill();
               DannysAot.LOGGER.info("Killed TitanEntity (eatingTarget) that was eating {} during transformation", player.getName().getString());
               return;
            }
         }

         for (SmallTitanEntity smallTitan : level.getNonSpectatingEntities(SmallTitanEntity.class, searchBox)) {
            if (smallTitan.isEating() && smallTitan.getEatingTargetId() == player.getId()) {
               smallTitan.kill();
               DannysAot.LOGGER.info("Killed SmallTitan (eatingTarget) that was eating {} during transformation", player.getName().getString());
               return;
            }
         }

         for (SmallTitan2Entity smallTitan2 : level.getNonSpectatingEntities(SmallTitan2Entity.class, searchBox)) {
            if (smallTitan2.isEating() && smallTitan2.getEatingTargetId() == player.getId()) {
               smallTitan2.kill();
               DannysAot.LOGGER.info("Killed SmallTitan2 (eatingTarget) that was eating {} during transformation", player.getName().getString());
               return;
            }
         }

         for (FritzTitanEntity fritzTitan : level.getNonSpectatingEntities(FritzTitanEntity.class, searchBox)) {
            if (fritzTitan.isEating() && fritzTitan.getEatingTargetId() == player.getId()) {
               fritzTitan.kill();
               DannysAot.LOGGER.info("Killed FritzTitan (eatingTarget) that was eating {} during transformation", player.getName().getString());
               return;
            }
         }
      }
   }

   private static Vec3d getColossalNapePosition(ColossalTitanEntity titan) {
      double headHeight = 53.5;
      double forwardOffset = 5.0;
      float yawRad = (float)Math.toRadians(titan.getYaw());
      double offsetX = -Math.sin(yawRad) * forwardOffset;
      double offsetZ = Math.cos(yawRad) * forwardOffset;
      return new Vec3d(titan.getX() + offsetX, titan.getY() + headHeight, titan.getZ() + offsetZ);
   }

   private static Vec3d getAttackNapePosition(AttackTitanEntity titan) {
      double headHeight = 12.0;
      double forwardOffset = 1.5;
      float yawRad = (float)Math.toRadians(titan.getYaw());
      double offsetX = -Math.sin(yawRad) * forwardOffset;
      double offsetZ = Math.cos(yawRad) * forwardOffset;
      return new Vec3d(titan.getX() + offsetX, titan.getY() + headHeight, titan.getZ() + offsetZ);
   }

   private static Vec3d getArmoredNapePosition(ArmoredTitanEntity titan) {
      double headHeight = 12.0;
      double forwardOffset = 1.5;
      float yawRad = (float)Math.toRadians(titan.getYaw());
      double offsetX = -Math.sin(yawRad) * forwardOffset;
      double offsetZ = Math.cos(yawRad) * forwardOffset;
      return new Vec3d(titan.getX() + offsetX, titan.getY() + headHeight, titan.getZ() + offsetZ);
   }

   private static Vec3d getFemaleTitanNapePosition(FemaleTitanEntity titan) {
      double headHeight = 11.0;
      double forwardOffset = 1.5;
      float yawRad = (float)Math.toRadians(titan.getYaw());
      double offsetX = -Math.sin(yawRad) * forwardOffset;
      double offsetZ = Math.cos(yawRad) * forwardOffset;
      return new Vec3d(titan.getX() + offsetX, titan.getY() + headHeight, titan.getZ() + offsetZ);
   }

   private static Vec3d getBeastNapePosition(BeastTitanEntity titan) {
      double headHeight = 14.0;
      double forwardOffset = 1.5;
      float yawRad = (float)Math.toRadians(titan.getYaw());
      double offsetX = -Math.sin(yawRad) * forwardOffset;
      double offsetZ = Math.cos(yawRad) * forwardOffset;
      return new Vec3d(titan.getX() + offsetX, titan.getY() + headHeight, titan.getZ() + offsetZ);
   }

   private static Vec3d getWarhammerNapePosition(WarhammerTitanEntity titan) {
      double headHeight = 10.0;
      double forwardOffset = 1.5;
      float yawRad = (float)Math.toRadians(titan.getYaw());
      double offsetX = -Math.sin(yawRad) * forwardOffset;
      double offsetZ = Math.cos(yawRad) * forwardOffset;
      return new Vec3d(titan.getX() + offsetX, titan.getY() + headHeight, titan.getZ() + offsetZ);
   }

   private static void tickAscendingPlayers(MinecraftServer server) {
      Iterator<Entry<UUID, ModNetworking.AscendingPlayer>> iterator = ascendingPlayers.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, ModNetworking.AscendingPlayer> entry = iterator.next();
         UUID playerUUID = entry.getKey();
         ModNetworking.AscendingPlayer ascending = entry.getValue();
         Entity titanEntity = ascending.titan();
         ModNetworking.ShifterType shifterType = ascending.shifterType();
         ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUUID);
         if (player != null && !player.isRemoved() && player.isAlive() && titanEntity != null && !titanEntity.isRemoved() && titanEntity.isAlive()) {
            player.setInvisible(true);
            player.noClip = true;
            Vec3d target;
            if (shifterType == ModNetworking.ShifterType.OGRE_SHIFTER && titanEntity instanceof OgreShifterTitanEntity ogreTitan) {
               target = ogreTitan.getPassengerRidingPos(player);
            } else if ((shifterType == ModNetworking.ShifterType.JAW || shifterType == ModNetworking.ShifterType.CART)
               && titanEntity instanceof TestShifterTitanEntity testTitan) {
               target = testTitan.getPassengerRidingPos(player);
            } else if ((
                  shifterType == ModNetworking.ShifterType.ATTACK
                     || shifterType == ModNetworking.ShifterType.TRIPLE_T
                     || shifterType == ModNetworking.ShifterType.FOUNDING
               )
               && titanEntity instanceof AttackTitanEntity attackTitan) {
               target = getAttackNapePosition(attackTitan);
            } else if (shifterType == ModNetworking.ShifterType.FEMALE && titanEntity instanceof FemaleTitanEntity femaleTitan) {
               target = getFemaleTitanNapePosition(femaleTitan);
            } else if (shifterType == ModNetworking.ShifterType.ARMORED && titanEntity instanceof ArmoredTitanEntity armoredTitan) {
               target = getArmoredNapePosition(armoredTitan);
            } else if (shifterType == ModNetworking.ShifterType.BEAST && titanEntity instanceof BeastTitanEntity beastTitan) {
               target = getBeastNapePosition(beastTitan);
            } else if (shifterType == ModNetworking.ShifterType.WARHAMMER && titanEntity instanceof WarhammerTitanEntity warhammerTitan) {
               target = getWarhammerNapePosition(warhammerTitan);
            } else if (shifterType == ModNetworking.ShifterType.PURE_TITAN && titanEntity instanceof LivingEntity pureTitan) {
               target = getPureTitanRidePosition(pureTitan);
            } else {
               if (!(titanEntity instanceof ColossalTitanEntity colossalTitan)) {
                  iterator.remove();
                  continue;
               }

               target = getColossalNapePosition(colossalTitan);
            }

            Vec3d current = player.getPos();
            Vec3d toTarget = target.subtract(current);
            double distance = toTarget.length();
            double snapThreshold = shifterType != ModNetworking.ShifterType.ATTACK
                  && shifterType != ModNetworking.ShifterType.TRIPLE_T
                  && shifterType != ModNetworking.ShifterType.FOUNDING
                  && shifterType != ModNetworking.ShifterType.OGRE_SHIFTER
                  && shifterType != ModNetworking.ShifterType.JAW
                  && shifterType != ModNetworking.ShifterType.CART
                  && shifterType != ModNetworking.ShifterType.ARMORED
                  && shifterType != ModNetworking.ShifterType.FEMALE
                  && shifterType != ModNetworking.ShifterType.BEAST
                  && shifterType != ModNetworking.ShifterType.WARHAMMER
               ? 3.0
               : 1.5;
            if (distance < snapThreshold) {
               player.noClip = false;
               player.setVelocity(Vec3d.ZERO);
               player.requestTeleport(target.x, target.y, target.z);
               if (shifterType == ModNetworking.ShifterType.OGRE_SHIFTER && titanEntity instanceof OgreShifterTitanEntity ogreTitan) {
                  ogreTitan.onPlayerShift(player);
               } else if ((shifterType == ModNetworking.ShifterType.JAW || shifterType == ModNetworking.ShifterType.CART)
                  && titanEntity instanceof TestShifterTitanEntity testTitan) {
                  testTitan.onPlayerShift(player);
               } else if ((
                     shifterType == ModNetworking.ShifterType.ATTACK
                        || shifterType == ModNetworking.ShifterType.TRIPLE_T
                        || shifterType == ModNetworking.ShifterType.FOUNDING
                  )
                  && titanEntity instanceof AttackTitanEntity attackTitan) {
                  attackTitan.onPlayerShift(player);
               } else if (shifterType == ModNetworking.ShifterType.FEMALE && titanEntity instanceof FemaleTitanEntity femaleTitan) {
                  femaleTitan.onPlayerShift(player);
               } else if (shifterType == ModNetworking.ShifterType.ARMORED && titanEntity instanceof ArmoredTitanEntity armoredTitan) {
                  armoredTitan.onPlayerShift(player);
               } else if (shifterType == ModNetworking.ShifterType.BEAST && titanEntity instanceof BeastTitanEntity beastTitan) {
                  beastTitan.onPlayerShift(player);
               } else if (shifterType == ModNetworking.ShifterType.WARHAMMER && titanEntity instanceof WarhammerTitanEntity warhammerTitan) {
                  warhammerTitan.onPlayerShift(player);
               } else if (shifterType == ModNetworking.ShifterType.PURE_TITAN) {
                  if (titanEntity instanceof SmallTitanEntity smallTitan) {
                     smallTitan.addSelfInjectionPassenger(player);
                  } else if (titanEntity instanceof SmallTitan2Entity smallTitan2) {
                     smallTitan2.addSelfInjectionPassenger(player);
                  } else if (titanEntity instanceof TitanEntity titan) {
                     titan.addSelfInjectionPassenger(player);
                  } else if (titanEntity instanceof SadTitanEntity sadTitan) {
                     sadTitan.addSelfInjectionPassenger(player);
                  } else if (titanEntity instanceof CrawlerTitanEntity crawlerTitan) {
                     crawlerTitan.addSelfInjectionPassenger(player);
                  } else if (titanEntity instanceof OgreTitanEntity ogreTitan) {
                     ogreTitan.addSelfInjectionPassenger(player);
                  }

                  player.startRiding(titanEntity, true);
                  player.setInvisible(true);
               } else if (titanEntity instanceof ColossalTitanEntity colossalTitan) {
                  colossalTitan.onPlayerShift(player);
               }

               iterator.remove();
               DannysAot.LOGGER.info("Player {} mounted {} titan at nape", player.getName().getString(), shifterType);
            } else {
               double speedMultiplier = Math.min(1.0, distance / 10.0);
               double adjustedSpeed = 2.5 * Math.max(0.3, speedMultiplier);
               Vec3d direction = toTarget.normalize();
               Vec3d velocity = direction.multiply(Math.min(adjustedSpeed, distance));
               player.setVelocity(velocity);
               player.velocityModified = true;
               ascendingPlayers.put(playerUUID, new ModNetworking.AscendingPlayer(titanEntity, ascending.ticksElapsed() + 1, shifterType));
               if (ascending.ticksElapsed() > 60) {
                  player.noClip = false;
                  player.requestTeleport(target.x, target.y, target.z);
                  if (shifterType == ModNetworking.ShifterType.OGRE_SHIFTER && titanEntity instanceof OgreShifterTitanEntity ogreTitan) {
                     ogreTitan.onPlayerShift(player);
                  } else if ((shifterType == ModNetworking.ShifterType.JAW || shifterType == ModNetworking.ShifterType.CART)
                     && titanEntity instanceof TestShifterTitanEntity testTitan) {
                     testTitan.onPlayerShift(player);
                  } else if ((
                        shifterType == ModNetworking.ShifterType.ATTACK
                           || shifterType == ModNetworking.ShifterType.TRIPLE_T
                           || shifterType == ModNetworking.ShifterType.FOUNDING
                     )
                     && titanEntity instanceof AttackTitanEntity attackTitan) {
                     attackTitan.onPlayerShift(player);
                  } else if (shifterType == ModNetworking.ShifterType.FEMALE && titanEntity instanceof FemaleTitanEntity femaleTitan) {
                     femaleTitan.onPlayerShift(player);
                  } else if (shifterType == ModNetworking.ShifterType.ARMORED && titanEntity instanceof ArmoredTitanEntity armoredTitan) {
                     armoredTitan.onPlayerShift(player);
                  } else if (shifterType == ModNetworking.ShifterType.BEAST && titanEntity instanceof BeastTitanEntity beastTitan) {
                     beastTitan.onPlayerShift(player);
                  } else if (shifterType == ModNetworking.ShifterType.WARHAMMER && titanEntity instanceof WarhammerTitanEntity warhammerTitan) {
                     warhammerTitan.onPlayerShift(player);
                  } else if (shifterType == ModNetworking.ShifterType.PURE_TITAN) {
                     if (titanEntity instanceof SmallTitanEntity smallTitan) {
                        smallTitan.addSelfInjectionPassenger(player);
                     } else if (titanEntity instanceof SmallTitan2Entity smallTitan2) {
                        smallTitan2.addSelfInjectionPassenger(player);
                     } else if (titanEntity instanceof TitanEntity titan) {
                        titan.addSelfInjectionPassenger(player);
                     }

                     player.startRiding(titanEntity, true);
                     player.setInvisible(true);
                  } else if (titanEntity instanceof ColossalTitanEntity colossalTitan) {
                     colossalTitan.onPlayerShift(player);
                  }

                  iterator.remove();
                  DannysAot.LOGGER.warn("Player {} ascent timed out, forcing mount", player.getName().getString());
               }
            }
         } else {
            if (player != null && !player.isRemoved()) {
               player.noClip = false;
            }

            iterator.remove();
         }
      }
   }

   private static void triggerTransformationExplosion(
      ServerWorld level, double x, double y, double z, ServerPlayerEntity shifter, double radius, float damage, double knockbackStrength
   ) {
      Vec3d center = new Vec3d(x, y, z);
      Box explosionBox = new Box(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);

      for (LivingEntity target : level.getNonSpectatingEntities(LivingEntity.class, explosionBox)) {
         if (target != shifter
            && !(target instanceof AttackTitanEntity)
            && !(target instanceof ArmoredTitanEntity)
            && !(target instanceof ColossalTitanEntity)
            && !(target instanceof FemaleTitanEntity)
            && !(target instanceof BeastTitanEntity)
            && !(target instanceof WarhammerTitanEntity)
            && !(target instanceof AttackTitanNapeEntity)
            && !(target instanceof AttackTitanEyeEntity)
            && !(target instanceof ArmoredTitanNapeEntity)
            && !(target instanceof ArmoredTitanEyeEntity)
            && !(target instanceof ColossalTitanNapeEntity)
            && !(target instanceof ColossalTitanEyeEntity)
            && !(target instanceof FemaleTitanNapeEntity)
            && !(target instanceof FemaleTitanEyeEntity)
            && !(target instanceof BeastTitanNapeEntity)
            && !(target instanceof BeastTitanEyeEntity)
            && !(target instanceof WarhammerTitanNapeEntity)
            && !(target instanceof WarhammerTitanEyeEntity)
            && !(
               target instanceof PlayerEntity p
                  && (
                     p.getVehicle() instanceof AttackTitanEntity
                        || p.getVehicle() instanceof ArmoredTitanEntity
                        || p.getVehicle() instanceof ColossalTitanEntity
                        || p.getVehicle() instanceof FemaleTitanEntity
                        || p.getVehicle() instanceof BeastTitanEntity
                        || p.getVehicle() instanceof WarhammerTitanEntity
                  )
            )) {
            double distance = target.getPos().distanceTo(center);
            if (!(distance > radius)) {
               double intensity = 1.0 - distance / radius;
               intensity *= intensity;
               float actualDamage = (float)(damage * intensity);
               if (actualDamage > 0.0F) {
                  target.damage(level.getDamageSources().explosion(null, shifter), actualDamage);
               }

               Vec3d knockbackDir = target.getPos().subtract(center);
               if (knockbackDir.lengthSquared() > 0.001) {
                  knockbackDir = knockbackDir.normalize();
               } else {
                  knockbackDir = new Vec3d(level.random.nextDouble() - 0.5, 0.5, level.random.nextDouble() - 0.5).normalize();
               }

               double actualKnockback = knockbackStrength * intensity;
               Vec3d knockbackVelocity = knockbackDir.multiply(actualKnockback, actualKnockback * 0.5, actualKnockback);
               target.setVelocity(target.getVelocity().add(knockbackVelocity));
               target.velocityModified = true;
            }
         }
      }

      DannysAot.LOGGER.info("Transformation explosion: radius={}, damage={}, at ({}, {}, {})", new Object[]{radius, damage, x, y, z});
      ShiftShakePayload shiftShake = new ShiftShakePayload(x, y, z);

      for (ServerPlayerEntity px : level.getServer().getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(px, shiftShake);
      }
   }

   private static void handleODMHookUpdate(ServerPlayerEntity player, ODMHookUpdatePayload payload) {
      if (player.hasVehicle()) {
         ServerHookTracker.updateHookState(player.getUuid(), false, false);
      } else {
         ServerHookTracker.updateHookState(
            player.getUuid(),
            payload.leftActive() && !payload.leftExtending() && !payload.leftRetracting(),
            payload.rightActive() && !payload.rightExtending() && !payload.rightRetracting()
         );
         handleHookOnEntityDamage(player, payload);
         trackOgreHooks(player, payload);
         trackFemaleTitanHooks(player, payload);
         if (payload.isBoosting() && player.getWorld() instanceof ServerWorld serverLevel) {
            Vec3d velocity = player.getVelocity();
            Vec3d playerPos = player.getPos();
            Vec3d particleDirection = velocity.normalize().multiply(-1.0);

            for (int i = 0; i < 2; i++) {
               double offsetX = (player.getRandom().nextDouble() - 0.5) * 0.3;
               double offsetY = (player.getRandom().nextDouble() - 0.5) * 0.3;
               double offsetZ = (player.getRandom().nextDouble() - 0.5) * 0.3;
               serverLevel.spawnParticles(
                  daot.compat.BackportEffects.WHITE_SMOKE,
                  playerPos.x + particleDirection.x * 0.5 + offsetX,
                  playerPos.y + 0.5 + offsetY,
                  playerPos.z + particleDirection.z * 0.5 + offsetZ,
                  1,
                  0.0,
                  0.0,
                  0.0,
                  0.02
               );
            }

            for (int i = 0; i < 2; i++) {
               double offsetX = (player.getRandom().nextDouble() - 0.5) * 0.4;
               double offsetY = (player.getRandom().nextDouble() - 0.5) * 0.4;
               double offsetZ = (player.getRandom().nextDouble() - 0.5) * 0.4;
               serverLevel.spawnParticles(
                  ParticleTypes.CLOUD,
                  playerPos.x + particleDirection.x * 0.5 + offsetX,
                  playerPos.y + 0.5 + offsetY,
                  playerPos.z + particleDirection.z * 0.5 + offsetZ,
                  1,
                  0.0,
                  0.0,
                  0.0,
                  0.01
               );
            }
         }

         ODMHookSyncPayload syncPayload = new ODMHookSyncPayload(
            player.getId(),
            payload.leftActive(),
            payload.leftExtending(),
            payload.leftRetracting(),
            payload.leftX(),
            payload.leftY(),
            payload.leftZ(),
            payload.leftStartX(),
            payload.leftStartY(),
            payload.leftStartZ(),
            payload.rightActive(),
            payload.rightExtending(),
            payload.rightRetracting(),
            payload.rightX(),
            payload.rightY(),
            payload.rightZ(),
            payload.rightStartX(),
            payload.rightStartY(),
            payload.rightStartZ(),
            payload.isBoosting()
         );

         for (ServerPlayerEntity nearby : PlayerLookup.tracking(player)) {
            if (nearby != player) {
               ServerPlayNetworking.send(nearby, syncPayload);
            }
         }
      }
   }

   private static void handleHookOnEntityDamage(ServerPlayerEntity hooker, ODMHookUpdatePayload payload) {
      int[] prev = previousHookedEntities.getOrDefault(hooker.getUuid(), new int[]{-1, -1});
      boolean leftLatched = payload.leftActive() && !payload.leftExtending() && !payload.leftRetracting();
      boolean rightLatched = payload.rightActive() && !payload.rightExtending() && !payload.rightRetracting();
      int leftId = leftLatched ? payload.leftHookedEntityId() : -1;
      int rightId = rightLatched ? payload.rightHookedEntityId() : -1;
      if (leftId != -1 && leftId != prev[0]) {
         applyHookDamage(hooker, leftId);
      }

      if (rightId != -1 && rightId != prev[1]) {
         applyHookDamage(hooker, rightId);
      }

      previousHookedEntities.put(hooker.getUuid(), new int[]{leftId, rightId});
   }

   private static void trackOgreHooks(ServerPlayerEntity player, ODMHookUpdatePayload payload) {
      boolean leftLatched = payload.leftActive() && !payload.leftExtending() && !payload.leftRetracting();
      boolean rightLatched = payload.rightActive() && !payload.rightExtending() && !payload.rightRetracting();
      int leftId = leftLatched ? payload.leftHookedEntityId() : -1;
      int rightId = rightLatched ? payload.rightHookedEntityId() : -1;
      Set<OgreTitanEntity> hookedOgres = new HashSet<>();

      for (int id : new int[]{leftId, rightId}) {
         if (id != -1) {
            Entity entity = player.getWorld().getEntityById(id);
            if (entity instanceof OgreTitanEntity ogre) {
               hookedOgres.add(ogre);
            } else if (entity instanceof OgreTitanNapeEntity nape) {
               OgreTitanEntity ogre = nape.getParentTitan();
               if (ogre != null) {
                  hookedOgres.add(ogre);
               }
            }
         }
      }

      UUID playerUUID = player.getUuid();

      for (Entity entity : player.getWorld().getOtherEntities(player, player.getBoundingBox().expand(100.0), e -> e instanceof OgreTitanEntity)) {
         OgreTitanEntity ogre = (OgreTitanEntity)entity;
         ogre.setPlayerHooked(playerUUID, hookedOgres.contains(ogre));
      }
   }

   private static void trackFemaleTitanHooks(ServerPlayerEntity player, ODMHookUpdatePayload payload) {
      boolean leftLatched = payload.leftActive() && !payload.leftExtending() && !payload.leftRetracting();
      boolean rightLatched = payload.rightActive() && !payload.rightExtending() && !payload.rightRetracting();
      int leftId = leftLatched ? payload.leftHookedEntityId() : -1;
      int rightId = rightLatched ? payload.rightHookedEntityId() : -1;
      Set<FemaleTitanEntity> hookedFemales = new HashSet<>();

      for (int id : new int[]{leftId, rightId}) {
         if (id != -1) {
            Entity entity = player.getWorld().getEntityById(id);
            if (entity instanceof FemaleTitanEntity ft) {
               hookedFemales.add(ft);
            } else if (entity instanceof FemaleTitanNapeEntity nape) {
               FemaleTitanEntity ft = nape.getParentTitan();
               if (ft != null) {
                  hookedFemales.add(ft);
               }
            } else if (entity instanceof FemaleTitanEyeEntity eye) {
               FemaleTitanEntity ft = eye.getParentTitan();
               if (ft != null) {
                  hookedFemales.add(ft);
               }
            }
         }
      }

      UUID playerUUID = player.getUuid();

      for (Entity entity : player.getWorld().getOtherEntities(player, player.getBoundingBox().expand(100.0), e -> e instanceof FemaleTitanEntity)) {
         FemaleTitanEntity ft = (FemaleTitanEntity)entity;
         ft.setPlayerHooked(playerUUID, hookedFemales.contains(ft));
      }
   }

   private static void applyHookDamage(ServerPlayerEntity hooker, int targetEntityId) {
      Entity entity = hooker.getWorld().getEntityById(targetEntityId);
      if (entity instanceof LivingEntity target && entity != hooker) {
         if (target instanceof TitanEntity
            || target instanceof SmallTitanEntity
            || target instanceof SmallTitan2Entity
            || target instanceof FritzTitanEntity
            || target instanceof AttackTitanEntity
            || target instanceof ArmoredTitanEntity
            || target instanceof ColossalTitanEntity
            || target instanceof BeastTitanEntity
            || target instanceof TitanEyeEntity
            || target instanceof TitanNapeEntity
            || target instanceof SmallTitanEyeEntity
            || target instanceof SmallTitanNapeEntity
            || target instanceof SmallTitan2EyeEntity
            || target instanceof SmallTitan2NapeEntity
            || target instanceof FritzTitanEyeEntity
            || target instanceof FritzTitanNapeEntity
            || target instanceof AttackTitanEyeEntity
            || target instanceof AttackTitanNapeEntity
            || target instanceof ArmoredTitanEyeEntity
            || target instanceof ArmoredTitanNapeEntity
            || target instanceof ColossalTitanEyeEntity
            || target instanceof ColossalTitanNapeEntity
            || target instanceof FemaleTitanEntity
            || target instanceof FemaleTitanEyeEntity
            || target instanceof FemaleTitanNapeEntity
            || target instanceof BeastTitanEyeEntity
            || target instanceof BeastTitanNapeEntity
            || target instanceof WarhammerTitanEntity
            || target instanceof WarhammerTitanEyeEntity
            || target instanceof WarhammerTitanNapeEntity
            || target instanceof ConnieFatherEntity
            || target instanceof ConnieFatherEyeEntity
            || target instanceof ConnieFatherNapeEntity
            || target instanceof OgreTitanEntity
            || target instanceof OgreTitanNapeEntity
            || target instanceof CrawlerTitanEntity
            || target instanceof CrawlerTitanNapeEntity
            || target instanceof CrawlerTitanEyeEntity
            || target instanceof TitanDummyEntity
            || target instanceof TitanDummyNapeEntity
            || target instanceof TitanDummyEyeEntity) {
            return;
         }

         RegistryKey<DamageType> hookDamageKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("dannys-aot", "odm_hook"));
         RegistryEntry<DamageType> hookDamageHolder = hooker.getWorld()
            .getRegistryManager()
            .getWrapperOrThrow(RegistryKeys.DAMAGE_TYPE)
            .getOrThrow(hookDamageKey);
         DamageSource hookDamage = new DamageSource(hookDamageHolder, hooker);
         target.damage(hookDamage, 1.0F);
         Vec3d knockback = target.getPos().subtract(hooker.getPos()).normalize().multiply(0.4).add(0.0, 0.15, 0.0);
         target.setVelocity(target.getVelocity().add(knockback));
         target.velocityModified = true;
      }
   }

   private static void handleBladeReload(ServerPlayerEntity player) {
      Entity vehicle = player.getVehicle();
      if (!(vehicle instanceof SmallTitanEntity)
         && !(vehicle instanceof SmallTitan2Entity)
         && !(vehicle instanceof TitanEntity)
         && !(vehicle instanceof FritzTitanEntity)) {
         ItemStack mainHand = player.getMainHandStack();
         ItemStack offHand = player.getOffHandStack();
         boolean hasMainHandBlade = mainHand.getItem() instanceof BladeItem;
         boolean hasOffHandBlade = offHand.getItem() instanceof BladeItem;
         if (hasMainHandBlade || hasOffHandBlade) {
            boolean mainHandLoaded = hasMainHandBlade && BladeItem.getBladeState(mainHand) != BladeItem.BladeState.EMPTY;
            boolean offHandLoaded = hasOffHandBlade && BladeItem.getBladeState(offHand) != BladeItem.BladeState.EMPTY;
            if (!mainHandLoaded && !offHandLoaded) {
               int bladesNeeded = 0;
               if (hasMainHandBlade) {
                  bladesNeeded++;
               }

               if (hasOffHandBlade) {
                  bladesNeeded++;
               }

               boolean isCreative = player.isCreative();
               int availableBlades = isCreative ? bladesNeeded : countBladeComponents(player);
               if (availableBlades < bladesNeeded) {
                  player.sendMessage(Text.literal("No blades in inventory").formatted(Formatting.RED), true);
                  DannysAot.LOGGER
                     .info(
                        "Player {} tried to reload but has insufficient blades ({}/{})",
                        new Object[]{player.getName().getString(), availableBlades, bladesNeeded}
                     );
                  return;
               }

               if (!isCreative) {
                  consumeBladeComponents(player, bladesNeeded);
               }

               if (hasMainHandBlade) {
                  BladeItem.reloadBlade(mainHand);
               }

               if (hasOffHandBlade) {
                  BladeItem.reloadBlade(offHand);
               }

               player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.UNSHEATHE, SoundCategory.PLAYERS, 1.0F, 1.0F);
               DannysAot.LOGGER.info("Player {} reloaded {} blades", player.getName().getString(), bladesNeeded);
            } else {
               if (hasMainHandBlade && mainHandLoaded) {
                  boolean isFresh = BladeItem.getBladeDamage(mainHand) == 0;
                  BladeItem.ejectBlade(mainHand, player, Hand.MAIN_HAND);
                  if (isFresh) {
                     player.getInventory().insertStack(new ItemStack(DannysAot.BLADE_COMPONENT));
                  }
               }

               if (hasOffHandBlade && offHandLoaded) {
                  boolean isFresh = BladeItem.getBladeDamage(offHand) == 0;
                  BladeItem.ejectBlade(offHand, player, Hand.OFF_HAND);
                  if (isFresh) {
                     player.getInventory().insertStack(new ItemStack(DannysAot.BLADE_COMPONENT));
                  }
               }

               float pitch = 1.5F + player.getRandom().nextFloat() * 0.1F;
               player.getWorld()
                  .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, pitch);
               DannysAot.LOGGER.info("Player {} manually ejected blades", player.getName().getString());
            }
         }
      }
   }

   private static int countBladeComponents(ServerPlayerEntity player) {
      int count = 0;

      for (ItemStack stack : player.getInventory().main) {
         if (stack.getItem() instanceof BladeComponentItem) {
            count += stack.getCount();
         }
      }

      return count;
   }

   private static void consumeBladeComponents(ServerPlayerEntity player, int amount) {
      int remaining = amount;

      for (int i = 0; i < player.getInventory().main.size() && remaining > 0; i++) {
         ItemStack stack = player.getInventory().main.get(i);
         if (stack.getItem() instanceof BladeComponentItem) {
            int toRemove = Math.min(remaining, stack.getCount());
            stack.decrement(toRemove);
            remaining -= toRemove;
         }
      }
   }

   public static void initiatePureTitanShift(ServerPlayerEntity player, ServerWorld level, String ownerName) {
      if (ownerName != null) {
         pureTitanOwnerNames.put(player.getUuid(), ownerName);
      }

      initiatePureTitanShift(player, level);
   }

   public static void initiatePureTitanShift(ServerPlayerEntity player, ServerWorld level) {
      if (!pendingShifts.containsKey(player.getUuid())) {
         Identifier pathsDim = new Identifier("dannys-aot", "paths");
         if (player.getWorld().getRegistryKey().getValue().equals(pathsDim)) {
            player.sendMessage(Text.literal("Cannot transform in the Paths"), true);
         } else {
            ServerPlayNetworking.send(player, new PreshiftStartPayload(false, false));
            Set<ServerPlayerEntity> nearbyPlayers = new HashSet<>(PlayerLookup.tracking(player));
            PreshiftEffectPayload effectPayload = new PreshiftEffectPayload(player.getId(), false);

            for (ServerPlayerEntity nearby : nearbyPlayers) {
               if (nearby != player) {
                  ServerPlayNetworking.send(nearby, effectPayload);
               }
            }

            double px = player.getX();
            double py = player.getY() + 1.0;
            double pz = player.getZ();
            BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
            level.spawnParticles(bloodParticle, px, py, pz, 30, 0.3, 0.4, 0.3, 0.1);
            int delayTicks = 40 + player.getRandom().nextInt(11) * 2;
            pendingShifts.put(
               player.getUuid(),
               new ModNetworking.PendingShift(player.getX(), player.getY(), player.getZ(), player.getYaw(), delayTicks, ModNetworking.ShifterType.PURE_TITAN)
            );
            DannysAot.LOGGER.info("Player {} initiated pure titan shift via self-injection", player.getName().getString());
         }
      }
   }

   private static void handlePowerTransferToPlayer(ServerPlayerEntity player, TitanPowerType power, Entity titan, ServerWorld level) {
      double x = titan.getX();
      double y = titan.getY();
      double z = titan.getZ();
      if (titan instanceof TitanEntity t) {
         t.removeSelfInjectionPassenger(player);
      } else if (titan instanceof SmallTitanEntity st) {
         st.removeSelfInjectionPassenger(player);
      } else if (titan instanceof SmallTitan2Entity st2) {
         st2.removeSelfInjectionPassenger(player);
      }

      player.setInvisible(false);
      player.stopRiding();
      player.requestTeleport(x, y, z);
      if (titan instanceof LivingEntity le) {
         le.kill();
      }

      TitanPowerData data = TitanPowerData.get(level);
      GrantProvenance earned = GrantProvenance.ofInheritance(level.getServer());
      if (level.getGameRules().getBoolean(DannysAot.RULE_MULTIPLE_SHIFTERS)) {
         data.addPlayerToPower(player.getUuid(), power, earned);
      } else {
         data.setPlayerPower(player.getUuid(), power, earned);
      }

      player.addCommandTag(power.getTagName());
      player.sendMessage(
         Text.literal("You inherited the " + power.getDisplayName() + " power!")
            .styled(style -> style.withColor(power == TitanPowerType.ATTACK ? 5635925 : 16733525)),
         false
      );
      ModCriteriaTriggers.INHERITED_TITAN_POWER.trigger(player);
      DannysAot.LOGGER.info("Player {} acquired {} power via death event transfer", player.getName().getString(), power.getDisplayName());
   }

   private static void handleTitanTransformToVillager(Entity titan, TitanPowerType power, ServerWorld level) {
      double x = titan.getX();
      double y = titan.getY();
      double z = titan.getZ();
      float yaw = titan.getYaw();
      if (titan instanceof LivingEntity le) {
         le.kill();
      }

      VillagerEntity villager = EntityType.VILLAGER.create(level);
      if (villager != null) {
         villager.refreshPositionAndAngles(x, y, z, yaw, 0.0F);
         villager.initialize(level, level.getLocalDifficulty(villager.getBlockPos()), SpawnReason.CONVERSION, null, null);
         villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.NONE));
         level.spawnEntity(villager);
         TitanPowerData data = TitanPowerData.get(level);
         data.setPower(villager.getUuid(), power);
         TitanPowerHelper.broadcastPowerAdd(level, villager.getId(), power);
         DannysAot.LOGGER.info("Titan transformed into powered villager with {} power via death event", power.getDisplayName());
      }
   }

   private static void handleTitanRoar(ServerPlayerEntity player) {
      BloodlineData roarBloodlineData = BloodlineData.get(player.getServerWorld());
      BloodlineType roarBloodline = roarBloodlineData.getBloodline(player.getUuid());
      if (roarBloodline != BloodlineType.ROYAL && !player.getCommandTags().contains("titan_bloodline")) {
         player.sendMessage(Text.literal("Only Royals can use this Ability").styled(style -> style.withColor(Formatting.RED)), true);
      } else {
         Integer cooldownExpiry = awakenCooldownTicks.get(player.getUuid());
         if (cooldownExpiry != null && player.server.getTicks() < cooldownExpiry) {
            int remainingTicks = cooldownExpiry - player.server.getTicks();
            int remainingSeconds = (remainingTicks + 19) / 20;
            player.sendMessage(Text.literal("Awaken on cooldown (" + remainingSeconds + "s)").styled(style -> style.withColor(Formatting.RED)), true);
         } else if (awakenActive.contains(player.getUuid())) {
            releaseAllTitans(player);
            awakenActive.remove(player.getUuid());
            awakenCooldownTicks.put(player.getUuid(), player.server.getTicks() + 300);
         } else {
            if (player.getVehicle() instanceof BeastTitanEntity beastTitan) {
               beastTitan.triggerRoar();
            }

            ServerWorld level = player.getServerWorld();
            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();
            String shouterName = player.getName().getString();
            double radius = 200.0;
            Box searchBox = new Box(px - radius, py - radius, pz - radius, px + radius, py + radius, pz + radius);
            int transformedCount = 0;

            for (VillagerEntity villager : level.getNonSpectatingEntities(VillagerEntity.class, searchBox)) {
               if (VillagerTransformTracker.isInjected(villager) && VillagerTransformTracker.isVillagerRoyalFluid(villager)) {
                  String injectorName = VillagerTransformTracker.getVillagerInjectorName(villager);
                  if (shouterName.equals(injectorName) && !VillagerTransformTracker.isTransforming(villager)) {
                     int delayTicks = 40 + player.getRandom().nextInt(11) * 2;
                     VillagerTransformTracker.startTransformation(villager, level, delayTicks);
                     VillagerTransformPayload payload = new VillagerTransformPayload(villager.getId());
                     Collection<ServerPlayerEntity> trackingPlayers = PlayerLookup.tracking(villager);

                     for (ServerPlayerEntity nearby : trackingPlayers) {
                        ServerPlayNetworking.send(nearby, payload);
                     }

                     if (!trackingPlayers.contains(player)) {
                        ServerPlayNetworking.send(player, payload);
                     }

                     transformedCount++;
                  }
               }
            }

            int playerTransformCount = 0;
            BloodlineData bloodlineData = BloodlineData.get(level.getServer());

            for (ServerPlayerEntity targetPlayer : level.getServer().getPlayerManager().getPlayerList()) {
               if (targetPlayer != player
                  && VillagerTransformTracker.isPlayerInjected(targetPlayer)
                  && VillagerTransformTracker.isPlayerRoyalFluid(targetPlayer)) {
                  String injectorName = VillagerTransformTracker.getPlayerInjectorName(targetPlayer);
                  if (shouterName.equals(injectorName)) {
                     BloodlineType targetBloodline = bloodlineData.getBloodline(targetPlayer.getUuid());
                     if (targetBloodline != BloodlineType.ACKERMAN && targetBloodline != BloodlineType.MARLEYAN) {
                        double distSq = targetPlayer.squaredDistanceTo(px, py, pz);
                        if (!(distSq > radius * radius) && !pendingShifts.containsKey(targetPlayer.getUuid())) {
                           VillagerTransformTracker.removeInjectedPlayer(targetPlayer);
                           targetPlayer.sendMessage(Text.literal("Your blood begins to boil..").formatted(Formatting.GOLD, Formatting.BOLD));
                           initiatePureTitanShift(targetPlayer, targetPlayer.getServerWorld(), shouterName);
                           playerTransformCount++;
                        }
                     }
                  }
               }
            }

            int totalTransformed = transformedCount + playerTransformCount;
            float playerMax = playerMaxStamina.getOrDefault(player.getUuid(), 2000.0F);
            float currentStamina = playerStamina.getOrDefault(player.getUuid(), playerMax);
            float activationDrain = playerMax * 0.2F;
            if (currentStamina < activationDrain && !isBeastStaminaImmune(player.getUuid())) {
               player.sendMessage(Text.literal("Not enough stamina to Awaken").styled(style -> style.withColor(Formatting.RED)), true);
            } else {
               if (!isBeastStaminaImmune(player.getUuid())) {
                  float entityDrain = playerMax * 0.025F * totalTransformed;
                  playerStamina.put(player.getUuid(), Math.max(0.0F, currentStamina - activationDrain - entityDrain));
               }

               awakenActive.add(player.getUuid());
               broadcastAwakenToggle(player, true);
               roarBlockTicks.put(player.getUuid(), player.server.getTicks() + 120);
               awakenCooldownTicks.put(player.getUuid(), player.server.getTicks() + 300);
               level.playSound(null, px, py, pz, ModSounds.ROYAL_SHOUT, SoundCategory.PLAYERS, 6.0F, 1.0F);
               broadcastGoldMessage(player, "Awaken.");
               ServerPlayNetworking.send(player, new EffectPayload("royal_shout", px, py, pz, 1.0F));
               if (totalTransformed > 0) {
                  DannysAot.LOGGER
                     .info(
                        "Player {} used Royal Shout - transformed {} villagers and {} players within 200 blocks",
                        new Object[]{player.getName().getString(), transformedCount, playerTransformCount}
                     );
               } else {
                  DannysAot.LOGGER
                     .info("Player {} used Royal Shout - no entities injected with their spinal fluid found within 200 blocks", player.getName().getString());
               }
            }
         }
      }
   }

   private static void releaseAllTitans(ServerPlayerEntity player) {
      player.getServerWorld()
         .playSound(null, player.getX(), player.getY(), player.getZ(), daot.compat.BackportEffects.OMINOUS_ACTIVATE, SoundCategory.PLAYERS, 4.0F, 0.5F);
      String playerName = player.getName().getString();
      String ownerTag = "dannysaot_roar_owner:" + playerName;
      VillagerTransformTracker.clearActiveTarget(playerName);
      targetModes.remove(player.getUuid());
      activeTargetUUIDs.remove(player.getUuid());
      activeTargetNames.remove(player.getUuid());
      activeStopHeartbeats.remove(player.getUuid());
      ServerPlayNetworking.send(player, new TargetModeSyncPayload("none"));
      ServerPlayNetworking.send(player, new TargetGlowPayload(new int[0]));
      broadcastAwakenToggle(player, false);

      for (ServerWorld level : player.server.getWorlds()) {
         Box searchBox = new Box(
            player.getX() - 10000.0,
            player.getY() - 10000.0,
            player.getZ() - 10000.0,
            player.getX() + 10000.0,
            player.getY() + 10000.0,
            player.getZ() + 10000.0
         );

         for (HostileEntity mob : level.getNonSpectatingEntities(HostileEntity.class, searchBox)) {
            if (mob.getCommandTags().contains(ownerTag)) {
               mob.removeScoreboardTag(ownerTag);
               mob.removeScoreboardTag("dannysaot_commanded_stop");
               mob.removeScoreboardTag("dannysaot_regroup_no_collision");
               VillagerTransformTracker.clearRegroup(mob);
            }
         }
      }
   }

   private static void releaseAllTitansOnDeath(ServerPlayerEntity player) {
      String playerName = player.getName().getString();
      String ownerTag = "dannysaot_roar_owner:" + playerName;
      VillagerTransformTracker.clearActiveTarget(playerName);
      targetModes.remove(player.getUuid());
      activeTargetUUIDs.remove(player.getUuid());
      activeTargetNames.remove(player.getUuid());
      activeStopHeartbeats.remove(player.getUuid());

      for (ServerWorld level : player.server.getWorlds()) {
         Box searchBox = new Box(
            player.getX() - 10000.0,
            player.getY() - 10000.0,
            player.getZ() - 10000.0,
            player.getX() + 10000.0,
            player.getY() + 10000.0,
            player.getZ() + 10000.0
         );

         for (HostileEntity mob : level.getNonSpectatingEntities(HostileEntity.class, searchBox)) {
            if (mob.getCommandTags().contains(ownerTag)) {
               mob.removeScoreboardTag(ownerTag);
               mob.removeScoreboardTag("dannysaot_commanded_stop");
               mob.removeScoreboardTag("dannysaot_regroup_no_collision");
               VillagerTransformTracker.clearRegroup(mob);
            }
         }
      }
   }

   public static boolean isAwakenActive(UUID playerUUID) {
      return awakenActive.contains(playerUUID);
   }

   public static void releaseAllTitansOnDisconnect(ServerPlayerEntity player) {
      releaseAllTitansOnDeath(player);
      awakenActive.remove(player.getUuid());
   }

   private static void tickAwakenStamina(MinecraftServer server) {
      if (!awakenActive.isEmpty()) {
         Iterator<UUID> it = awakenActive.iterator();

         while (it.hasNext()) {
            UUID playerUUID = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUUID);
            if (player == null) {
               it.remove();
            } else {
               String playerName = player.getName().getString();
               String ownerTag = "dannysaot_roar_owner:" + playerName;
               boolean hasActiveTarget = VillagerTransformTracker.hasActiveTarget(playerName);
               int totalOwned = 0;
               int nonStoppedTitans = 0;
               int pursuingTitans = 0;

               for (ServerWorld level : server.getWorlds()) {
                  Box searchBox = new Box(
                     player.getX() - 500.0, player.getY() - 500.0, player.getZ() - 500.0, player.getX() + 500.0, player.getY() + 500.0, player.getZ() + 500.0
                  );

                  for (HostileEntity mob : level.getNonSpectatingEntities(HostileEntity.class, searchBox)) {
                     if (mob.getCommandTags().contains(ownerTag)) {
                        totalOwned++;
                        boolean thisStopped = mob.getCommandTags().contains("dannysaot_commanded_stop");
                        if (!thisStopped) {
                           nonStoppedTitans++;
                           if (hasActiveTarget) {
                              pursuingTitans++;
                           }
                        }
                     }
                  }
               }

               if (totalOwned != 0 && !isBeastStaminaImmune(playerUUID)) {
                  float playerMax = playerMaxStamina.getOrDefault(playerUUID, 2000.0F);
                  float stamina = playerStamina.getOrDefault(playerUUID, playerMax);
                  boolean allStopped = nonStoppedTitans == 0;
                  float drain = 0.0F;
                  if (!allStopped) {
                     drain += playerMax * 0.001F / 20.0F;
                  }

                  drain += playerMax * 0.00125F / 20.0F * nonStoppedTitans;
                  drain += playerMax * 0.001875F / 20.0F * pursuingTitans;
                  stamina = Math.max(0.0F, stamina - drain);
                  playerStamina.put(playerUUID, stamina);
                  if (stamina <= 0.0F) {
                     releaseAllTitans(player);
                     it.remove();
                     player.sendMessage(Text.literal("Your titans are no longer under your control!").formatted(Formatting.RED, Formatting.BOLD), true);
                  }
               }
            }
         }
      }
   }

   private static void tickZekesGlassesGlow(MinecraftServer server) {
      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         ItemStack headSlot = player.getEquippedStack(EquipmentSlot.HEAD);
         if (!headSlot.isEmpty() && headSlot.getItem() instanceof ZekesGlassesItem) {
            ServerWorld level = player.getServerWorld();
            TitanPowerData data = TitanPowerData.get(level);
            List<Integer> glowIds = new ArrayList<>();
            double radius = 64.0;
            Box searchBox = new Box(
               player.getX() - radius, player.getY() - radius, player.getZ() - radius, player.getX() + radius, player.getY() + radius, player.getZ() + radius
            );

            for (VillagerEntity villager : level.getNonSpectatingEntities(VillagerEntity.class, searchBox)) {
               if (data.hasPower(villager.getUuid())) {
                  glowIds.add(villager.getId());
               }
            }

            if (MCACompat.isMCALoaded()) {
               for (LivingEntity living : level.getNonSpectatingEntities(LivingEntity.class, searchBox)) {
                  if (!(living instanceof VillagerEntity)) {
                     String typeKey = Registries.ENTITY_TYPE.getId(living.getType()).toString();
                     if (typeKey.startsWith("mca:") && data.hasPower(living.getUuid())) {
                        glowIds.add(living.getId());
                     }
                  }
               }
            }

            int[] ids = glowIds.stream().mapToInt(Integer::intValue).toArray();
            ServerPlayNetworking.send(player, new ZekesGlowPayload(ids));
         } else {
            ServerPlayNetworking.send(player, new ZekesGlowPayload(new int[0]));
         }
      }
   }

   public static void scheduleStealthInvisibility(UUID playerUUID, int ticks) {
      stealthInvisibilityTimers.put(playerUUID, ticks);
   }

   private static void tickStealthInvisibility(MinecraftServer server) {
      if (!stealthInvisibilityTimers.isEmpty()) {
         Iterator<Entry<UUID, Integer>> iter = stealthInvisibilityTimers.entrySet().iterator();

         while (iter.hasNext()) {
            Entry<UUID, Integer> entry = iter.next();
            int remaining = entry.getValue() - 1;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (remaining <= 0) {
               iter.remove();
               if (player != null) {
                  player.setInvisible(false);
               }
            } else {
               entry.setValue(remaining);
               if (player != null) {
                  player.setInvisible(true);
               }
            }
         }
      }
   }

   private static void tickBladeChargeExpiry(MinecraftServer server) {
      if (!bladeChargeExpiry.isEmpty()) {
         long currentTick = server.getTicks();
         Iterator<Entry<UUID, Long>> iter = bladeChargeExpiry.entrySet().iterator();

         while (iter.hasNext()) {
            Entry<UUID, Long> entry = iter.next();
            if (currentTick >= entry.getValue()) {
               iter.remove();
               ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
               if (player != null) {
                  EntityAttributeInstance damageAttr = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
                  if (damageAttr != null) {
                     daot.compat.AttributeModifiers.removeModifier(damageAttr, BLADE_CHARGE_ID);
                  }
               }
            }
         }
      }
   }

   private static boolean anyOnlinePlayerHasPower(MinecraftServer server, TitanPowerType power) {
      String tag = power.getTagName();

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         if (player.getCommandTags().contains(tag)) {
            return true;
         }
      }

      return false;
   }

   private static boolean isRidingTitanNotInEject(PlayerEntity player) {
      return player.getVehicle() instanceof ShifterTitan shifter && !shifter.isDismounting();
   }

   private static void tickShifterRegeneration(MinecraftServer server) {
      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         boolean isShifter = player.getCommandTags().contains("attack")
            || player.getCommandTags().contains("colossal")
            || player.getCommandTags().contains("armored")
            || player.getCommandTags().contains("beast")
            || player.getCommandTags().contains("female")
            || player.getCommandTags().contains("warhammer")
            || player.getCommandTags().contains("founder")
            || player.getCommandTags().contains("triple_t")
            || player.getCommandTags().contains("ogre_shifter")
            || player.getCommandTags().contains("jaw")
            || player.getCommandTags().contains("cart_shifter");
         if (!isShifter || !player.isAlive() || !(player.getHealth() < player.getMaxHealth())) {
            StatusEffectInstance existing = player.getStatusEffect(StatusEffects.REGENERATION);
            if (existing != null && existing.isAmbient()) {
               player.removeStatusEffect(StatusEffects.REGENERATION);
            }
         } else if (player.getCommandTags().contains("titan_stealth")) {
            StatusEffectInstance stealthRegen = player.getStatusEffect(StatusEffects.REGENERATION);
            if (stealthRegen != null && stealthRegen.isAmbient()) {
               player.removeStatusEffect(StatusEffects.REGENERATION);
            }
         } else {
            float currentStamina = playerStamina.getOrDefault(player.getUuid(), getMaxStaminaForPlayer(player));
            if (currentStamina <= 0.0F) {
               StatusEffectInstance existingRegen = player.getStatusEffect(StatusEffects.REGENERATION);
               if (existingRegen != null && existingRegen.isAmbient()) {
                  player.removeStatusEffect(StatusEffects.REGENERATION);
               }
            } else {
               int regenAmplifier = player.getCommandTags().contains("ogre_shifter") ? 2 : 0;
               StatusEffectInstance existing = player.getStatusEffect(StatusEffects.REGENERATION);
               if (existing == null || existing.getDuration() < 40) {
                  player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, regenAmplifier, true, false, true));
               }

               if (BloodlineData.get(server).getRealBloodline(player.getUuid()) == BloodlineType.ROYAL) {
                  player.heal(0.15F * (1 << regenAmplifier) / 50.0F);
               }

               if (player.getVehicle() instanceof ArmoredTitanEntity ctAr && ctAr.isConsciousnessTransferActive()) {
                  player.heal(player.getMaxHealth() / 40.0F);
               }

               if (player.getWorld() instanceof ServerWorld serverLevel) {
                  double px = player.getX();
                  double py = player.getY() + player.getHeight() * 0.5;
                  double pz = player.getZ();
                  double offsetX = (player.getRandom().nextDouble() - 0.5) * 0.8;
                  double offsetY = (player.getRandom().nextDouble() - 0.5) * 0.6;
                  double offsetZ = (player.getRandom().nextDouble() - 0.5) * 0.8;
                  serverLevel.spawnParticles(DannysAot.PLAYER_DISMOUNT_PARTICLE, px + offsetX, py + offsetY, pz + offsetZ, 1, 0.0, 0.02, 0.0, 0.01);
                  if (server.getTicks() % 10 == 0) {
                     serverLevel.playSound(null, px, py, pz, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.15F, 1.0F);
                  }
               }
            }
         }
      }
   }

   public static void drainStamina(UUID playerUUID, float amount) {
      if (!isBeastStaminaImmune(playerUUID)) {
         float maxStam = getMaxStaminaForUUID(playerUUID);
         float current = playerStamina.getOrDefault(playerUUID, maxStam);
         playerStamina.put(playerUUID, Math.max(0.0F, current - amount));
      }
   }

   public static boolean isBeastStaminaImmune(UUID playerUUID) {
      return ModCommands.isRoyalBeastEnabled(playerUUID) || ogreStaminaImmune.contains(playerUUID);
   }

   public static float getStamina(UUID playerUUID) {
      float maxStam = getMaxStaminaForUUID(playerUUID);
      return playerStamina.getOrDefault(playerUUID, maxStam);
   }

   public static void fillStamina(UUID playerUUID) {
      float maxStam = getMaxStaminaForUUID(playerUUID);
      playerStamina.put(playerUUID, maxStam);
   }

   public static boolean isLowStamina(UUID playerUUID) {
      float maxStam = getMaxStaminaForUUID(playerUUID);
      float stamina = playerStamina.getOrDefault(playerUUID, maxStam);
      return stamina / maxStam < 0.1F;
   }

   private static void tickShifterStamina(MinecraftServer server) {
      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         if (player.getCommandTags().contains("ogre_shifter")) {
            ogreStaminaImmune.add(player.getUuid());
            LsoCompat.keepLimbsFull(player);
         } else {
            ogreStaminaImmune.remove(player.getUuid());
         }

         boolean isShifter = player.getCommandTags().contains("attack")
            || player.getCommandTags().contains("colossal")
            || player.getCommandTags().contains("armored")
            || player.getCommandTags().contains("beast")
            || player.getCommandTags().contains("female")
            || player.getCommandTags().contains("warhammer")
            || player.getCommandTags().contains("founder")
            || player.getCommandTags().contains("triple_t")
            || player.getCommandTags().contains("ogre_shifter")
            || player.getCommandTags().contains("jaw")
            || player.getCommandTags().contains("cart_shifter");
         if (!isShifter) {
            if (playerStamina.containsKey(player.getUuid())) {
               playerStamina.remove(player.getUuid());
               playerMaxStamina.remove(player.getUuid());
               ServerPlayNetworking.send(player, new StaminaSyncPayload(-1.0F, 0.0F));
            } else if (server.getTicks() % 100 == 0) {
               ServerPlayNetworking.send(player, new StaminaSyncPayload(-1.0F, 0.0F));
            }
         } else {
            float playerMax = getMaxStaminaForPlayer(player);
            playerMaxStamina.put(player.getUuid(), playerMax);
            float stamina = playerStamina.getOrDefault(player.getUuid(), playerMax);
            boolean inTitanForm = player.getVehicle() instanceof AttackTitanEntity
               || player.getVehicle() instanceof ArmoredTitanEntity
               || player.getVehicle() instanceof ColossalTitanEntity
               || player.getVehicle() instanceof FemaleTitanEntity
               || player.getVehicle() instanceof WarhammerTitanEntity;
            boolean staminaImmune = isBeastStaminaImmune(player.getUuid());
            if (inTitanForm) {
               float drain = 0.119F;
               if (player.getVehicle() instanceof LivingEntity vehicle && vehicle.isInSneakingPose()) {
                  drain *= 0.5F;
               }

               if (player.getVehicle() instanceof ArmoredTitanEntity armored) {
                  if (armored.isSprinting() && armored.isBreaching()) {
                     drain += 0.75F;
                  }

                  if (armored.isConsciousnessTransferActive()) {
                     drain *= 2.0F;
                  }
               }

               if (player.getVehicle() instanceof FemaleTitanEntity ft && ft.isHardened()) {
                  drain *= 2.0F;
               }

               if (player.getVehicle() instanceof AttackTitanEntity at && at.isArmsHardened()) {
                  drain *= 2.0F;
               }

               if (player.getVehicle() instanceof CartShifterTitanEntity) {
                  drain /= 20.0F;
               }

               if (staminaImmune) {
                  drain = 0.0F;
               }

               stamina = Math.max(0.0F, stamina - drain);
               if (stamina <= 0.0F && player.getVehicle() instanceof LivingEntity titanEntity) {
                  titanEntity.damage(player.getDamageSources().starve(), Float.MAX_VALUE);
               }
            } else {
               boolean isHealing = false;
               StatusEffectInstance regenEffect = player.getStatusEffect(StatusEffects.REGENERATION);
               if (regenEffect != null && regenEffect.isAmbient() && player.getHealth() < player.getMaxHealth()) {
                  isHealing = true;
               }

               if (isHealing && !staminaImmune) {
                  stamina = Math.max(0.0F, stamina - 0.12F);
               } else if (!isHealing && !activeStopHeartbeats.containsKey(player.getUuid())) {
                  float regenRate = player.getCommandTags().contains("beast") ? 0.714F : 0.238F;
                  if (BloodlineData.get(server).getRealBloodline(player.getUuid()) == BloodlineType.ROYAL) {
                     regenRate *= 1.15F;
                  }

                  stamina = Math.min(playerMax, stamina + regenRate);
               }
            }

            playerStamina.put(player.getUuid(), stamina);
            if (server.getTicks() % 10 == 0) {
               boolean hasBeast = player.getCommandTags().contains("beast");
               boolean hasFounding = player.getCommandTags().contains("founder");
               ServerPlayNetworking.send(player, new StaminaSyncPayload(stamina, playerMax, hasBeast, hasFounding));
            }
         }
      }
   }

   private static void syncPlayerPowerTags(MinecraftServer server) {
      for (ServerWorld level : server.getWorlds()) {
         TitanPowerData data = TitanPowerData.get(level);
         boolean anyPlayerHasAttack = anyOnlinePlayerHasPower(server, TitanPowerType.ATTACK);
         if (anyPlayerHasAttack) {
            List<UUID> toRemove = data.getVillagersWithPower(TitanPowerType.ATTACK);

            for (UUID villagerUUID : toRemove) {
               data.removePower(villagerUUID);
               Entity entity = level.getEntity(villagerUUID);
               if (entity != null) {
                  TitanPowerHelper.broadcastPowerRemoval(level, entity.getId());
               }
            }

            if (!toRemove.isEmpty()) {
               DannysAot.LOGGER.info("Removed Attack power from {} villagers (player has it)", toRemove.size());
            }
         }

         boolean anyPlayerHasColossal = anyOnlinePlayerHasPower(server, TitanPowerType.COLOSSAL);
         if (anyPlayerHasColossal) {
            List<UUID> toRemove = data.getVillagersWithPower(TitanPowerType.COLOSSAL);

            for (UUID villagerUUIDx : toRemove) {
               data.removePower(villagerUUIDx);
               Entity entity = level.getEntity(villagerUUIDx);
               if (entity != null) {
                  TitanPowerHelper.broadcastPowerRemoval(level, entity.getId());
               }
            }

            if (!toRemove.isEmpty()) {
               DannysAot.LOGGER.info("Removed Colossal power from {} villagers (player has it)", toRemove.size());
            }
         }

         boolean anyPlayerHasArmored = anyOnlinePlayerHasPower(server, TitanPowerType.ARMORED);
         if (anyPlayerHasArmored) {
            List<UUID> toRemove = data.getVillagersWithPower(TitanPowerType.ARMORED);

            for (UUID villagerUUIDxx : toRemove) {
               data.removePower(villagerUUIDxx);
               Entity entity = level.getEntity(villagerUUIDxx);
               if (entity != null) {
                  TitanPowerHelper.broadcastPowerRemoval(level, entity.getId());
               }
            }

            if (!toRemove.isEmpty()) {
               DannysAot.LOGGER.info("Removed Armored power from {} villagers (player has it)", toRemove.size());
            }
         }
      }
   }

   private static void handleThunderSpearLoad(ServerPlayerEntity player) {
      ItemStack mainHand = player.getMainHandStack();
      ItemStack offHand = player.getOffHandStack();
      boolean hasMainHandBlade = mainHand.getItem() instanceof BladeItem;
      boolean hasOffHandBlade = offHand.getItem() instanceof BladeItem;
      if (hasMainHandBlade || hasOffHandBlade) {
         boolean mainHasSpear = hasMainHandBlade && BladeItem.hasThunderSpear(mainHand);
         boolean offHasSpear = hasOffHandBlade && BladeItem.hasThunderSpear(offHand);
         if (!mainHasSpear && !offHasSpear) {
            int gripsNeeded = 0;
            if (hasMainHandBlade) {
               gripsNeeded++;
            }

            if (hasOffHandBlade) {
               gripsNeeded++;
            }

            boolean isCreative = player.isCreative();
            int available = isCreative ? gripsNeeded : countThunderSpears(player);
            if (available < gripsNeeded) {
               if (available <= 0) {
                  player.sendMessage(Text.literal("No thunder spears in inventory").formatted(Formatting.RED), true);
                  return;
               }

               gripsNeeded = available;
            }

            int loaded = 0;
            if (hasMainHandBlade && loaded < gripsNeeded) {
               BladeItem.setThunderSpear(mainHand, true);
               loaded++;
            }

            if (hasOffHandBlade && loaded < gripsNeeded) {
               BladeItem.setThunderSpear(offHand, true);
               loaded++;
            }

            if (!isCreative) {
               consumeThunderSpears(player, loaded);
            }

            player.getWorld()
               .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 1.0F, 1.2F);
         } else {
            if (mainHasSpear) {
               BladeItem.setThunderSpear(mainHand, false);
               if (!player.isCreative()) {
                  player.getInventory().insertStack(new ItemStack(DannysAot.THUNDER_SPEAR));
               }
            }

            if (offHasSpear) {
               BladeItem.setThunderSpear(offHand, false);
               if (!player.isCreative()) {
                  player.getInventory().insertStack(new ItemStack(DannysAot.THUNDER_SPEAR));
               }
            }

            player.getWorld()
               .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 1.0F, 0.8F);
         }
      }
   }

   private static int countThunderSpears(ServerPlayerEntity player) {
      int count = 0;

      for (ItemStack stack : player.getInventory().main) {
         if (stack.getItem() instanceof ThunderSpearItem) {
            count += stack.getCount();
         }
      }

      return count;
   }

   private static void consumeThunderSpears(ServerPlayerEntity player, int amount) {
      int remaining = amount;

      for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
         ItemStack stack = player.getInventory().getStack(i);
         if (stack.getItem() instanceof ThunderSpearItem) {
            int take = Math.min(stack.getCount(), remaining);
            stack.decrement(take);
            remaining -= take;
         }
      }
   }

   private static void handleThunderSpearFire(ServerPlayerEntity player, Hand hand, float clientXRot, float clientYRot) {
      if (!DefeatedCarryTracker.isIncapacitatedOrDefeated(player)) {
         if (!DannysAot.areThunderSpearsAllowed(player.getWorld())) {
            player.sendMessage(Text.literal("Thunder Spears have been disabled").styled(style -> style.withColor(Formatting.RED)), true);
         } else {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() instanceof BladeItem) {
               if (BladeItem.hasThunderSpear(stack)) {
                  BladeItem.setThunderSpear(stack, false);
                  float xRot = MathHelper.clamp(clientXRot, -90.0F, 90.0F);
                  boolean mainIsRight = player.getMainArm() == Arm.RIGHT;
                  boolean isLeftSide = hand == Hand.MAIN_HAND ? !mainIsRight : mainIsRight;
                  float xRotRad = (float)Math.toRadians(xRot);
                  float yRotRad = (float)Math.toRadians(clientYRot);
                  double lookX = -Math.sin(yRotRad) * Math.cos(xRotRad);
                  double lookY = -Math.sin(xRotRad);
                  double lookZ = Math.cos(yRotRad) * Math.cos(xRotRad);
                  Vec3d lookDir = new Vec3d(lookX, lookY, lookZ).normalize();
                  Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
                  Vec3d right = lookDir.crossProduct(worldUp).normalize();
                  float sideOffset = isLeftSide ? -0.4F : 0.4F;
                  double spawnX = player.getX() + right.x * sideOffset;
                  double spawnY = player.getY() + 0.9 + right.y * sideOffset;
                  double spawnZ = player.getZ() + right.z * sideOffset;
                  ThunderSpearEntity spear = new ThunderSpearEntity(player.getWorld(), player);
                  spear.setPosition(spawnX, spawnY, spawnZ);
                  spear.setVelocity(lookDir.x, lookDir.y, lookDir.z, 1.8F, 0.0F);
                  player.getWorld().spawnEntity(spear);
                  float shootPitch = 0.5F + player.getRandom().nextFloat() * 0.1F;
                  player.getWorld()
                     .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, shootPitch);
               }
            }
         }
      }
   }

   private static void handleAPGFire(ServerPlayerEntity player, float clientXRot, float clientYRot, int handMask) {
      ItemStack main = player.getMainHandStack();
      ItemStack off = player.getOffHandStack();
      boolean fireMain = (handMask & 1) != 0 && main.getItem() == DannysAot.APG_GUN && APGGunItem.isLoaded(main);
      boolean fireOff = (handMask & 2) != 0 && off.getItem() == DannysAot.APG_GUN && APGGunItem.isLoaded(off);
      if (fireMain || fireOff) {
         float xRot = MathHelper.clamp(clientXRot, -90.0F, 90.0F);
         float xRotRad = (float)Math.toRadians(xRot);
         float yRotRad = (float)Math.toRadians(clientYRot);
         double lookX = -Math.sin(yRotRad) * Math.cos(xRotRad);
         double lookY = -Math.sin(xRotRad);
         double lookZ = Math.cos(yRotRad) * Math.cos(xRotRad);
         Vec3d lookDir = new Vec3d(lookX, lookY, lookZ).normalize();
         Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
         Vec3d right = lookDir.crossProduct(worldUp).normalize();
         boolean mainIsRight = player.getMainArm() == Arm.RIGHT;
         if (fireMain) {
            spawnAPGBullet(player, lookDir, right, mainIsRight ? 1 : -1);
            APGGunItem.setLoaded(main, false);
            APGGunItem.triggerAnim(player, main, "fire");
         }

         if (fireOff) {
            spawnAPGBullet(player, lookDir, right, mainIsRight ? -1 : 1);
            APGGunItem.setLoaded(off, false);
            APGGunItem.triggerAnim(player, off, "fire");
         }

         apgLastFireTick.put(player.getUuid(), (long)player.getServer().getTicks());
         float shootPitch = 0.9F + player.getRandom().nextFloat() * 0.1F;
         player.getWorld().playSound(player, player.getX(), player.getY(), player.getZ(), ModSounds.GUN_SHOOT_APG, SoundCategory.PLAYERS, 1.0F, shootPitch);
         float shoot2Pitch = 1.1F + player.getRandom().nextFloat() * 0.1F;
         player.getWorld().playSound(player, player.getX(), player.getY(), player.getZ(), ModSounds.GUN_SHOOT_APG2, SoundCategory.PLAYERS, 0.3F, shoot2Pitch);
         player.getWorld().playSound(player, player.getX(), player.getY(), player.getZ(), ModSounds.TRIGGER, SoundCategory.PLAYERS, 0.6F, 1.0F);
         int handsFiring = Integer.bitCount(handMask);
         float nearbyShake = handsFiring == 2 ? 0.5F : 0.35F;
         double shakeRadiusSq = 1024.0;
         if (player.getWorld() instanceof ServerWorld srvLvl) {
            for (ServerPlayerEntity other : srvLvl.getPlayers()) {
               if (other != player && !(other.squaredDistanceTo(player) > shakeRadiusSq)) {
                  ServerPlayNetworking.send(other, new APGFireShakePayload(nearbyShake));
               }
            }
         }
      }
   }

   private static void handleAPGReload(ServerPlayerEntity player) {
      Long lastFire = apgLastFireTick.get(player.getUuid());
      if (lastFire == null || player.getServer().getTicks() - lastFire >= 13L) {
         ItemStack main = player.getMainHandStack();
         ItemStack off = player.getOffHandStack();
         boolean mainIsGun = main.getItem() == DannysAot.APG_GUN;
         boolean offIsGun = off.getItem() == DannysAot.APG_GUN;
         if (mainIsGun || offIsGun) {
            boolean mainLoaded = mainIsGun && APGGunItem.isLoaded(main);
            boolean offLoaded = offIsGun && APGGunItem.isLoaded(off);
            boolean isCreative = player.isCreative();
            boolean changed = false;
            boolean wasUnload = mainLoaded || offLoaded;
            if (wasUnload) {
               if (mainLoaded) {
                  APGGunItem.setLoaded(main, false);
                  if (!isCreative) {
                     ItemStack ret = new ItemStack(DannysAot.APG_CARTRIDGE);
                     if (!player.getInventory().insertStack(ret)) {
                        player.dropItem(ret, false);
                     }
                  }

                  APGGunItem.triggerAnim(player, main, "unload");
                  changed = true;
               }

               if (offLoaded) {
                  APGGunItem.setLoaded(off, false);
                  if (!isCreative) {
                     ItemStack ret = new ItemStack(DannysAot.APG_CARTRIDGE);
                     if (!player.getInventory().insertStack(ret)) {
                        player.dropItem(ret, false);
                     }
                  }

                  APGGunItem.triggerAnim(player, off, "unload");
                  changed = true;
               }
            } else {
               int needed = (mainIsGun ? 1 : 0) + (offIsGun ? 1 : 0);
               if (!isCreative) {
                  if (countCartridges(player) < needed) {
                     return;
                  }

                  consumeCartridges(player, needed);
               }

               if (mainIsGun) {
                  APGGunItem.setLoaded(main, true);
                  APGGunItem.triggerAnim(player, main, "load");
                  changed = true;
               }

               if (offIsGun) {
                  APGGunItem.setLoaded(off, true);
                  APGGunItem.triggerAnim(player, off, "load");
                  changed = true;
               }
            }

            if (changed) {
               player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.TRIGGER, SoundCategory.PLAYERS, 0.35F, 1.4F);
               float reloadPitch = 0.9F + player.getRandom().nextFloat() * 0.1F;
               player.getWorld()
                  .playSound(
                     null,
                     player.getX(),
                     player.getY(),
                     player.getZ(),
                     wasUnload ? ModSounds.APG_UNLOAD : ModSounds.APG_LOAD,
                     SoundCategory.PLAYERS,
                     1.0F,
                     reloadPitch
                  );
            }
         }
      }
   }

   private static int countCartridges(ServerPlayerEntity player) {
      int count = 0;
      PlayerInventory inv = player.getInventory();

      for (int i = 0; i < inv.size(); i++) {
         ItemStack s = inv.getStack(i);
         if (s.getItem() == DannysAot.APG_CARTRIDGE) {
            count += s.getCount();
         }
      }

      return count;
   }

   private static void consumeCartridges(ServerPlayerEntity player, int amount) {
      int remaining = amount;
      PlayerInventory inv = player.getInventory();

      for (int i = 0; i < inv.size() && remaining > 0; i++) {
         ItemStack s = inv.getStack(i);
         if (s.getItem() == DannysAot.APG_CARTRIDGE) {
            int take = Math.min(remaining, s.getCount());
            s.decrement(take);
            if (s.isEmpty()) {
               inv.setStack(i, ItemStack.EMPTY);
            }

            remaining -= take;
         }
      }
   }

   private static void spawnAPGBullet(ServerPlayerEntity player, Vec3d lookDir, Vec3d right, int sideSign) {
      if (player.getWorld() instanceof ServerWorld level) {
         Vec3d var17 = new Vec3d(player.getX(), player.getEyeY(), player.getZ());
         double offset = 0.35 * sideSign;
         Vec3d muzzle = new Vec3d(player.getX() + right.x * offset, player.getEyeY() - 0.15 + right.y * offset, player.getZ() + right.z * offset);
         Vec3d end = var17.add(lookDir.multiply(160.0));
         Vec3d segStart = var17;
         Vec3d blockHit = end;
         BlockState blockHitState = null;

         for (int ehr = 0; ehr < 24; ehr++) {
            BlockHitResult bhr = level.raycast(new RaycastContext(segStart, end, ShapeType.COLLIDER, FluidHandling.NONE, player));
            if (bhr.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
               blockHit = end;
               break;
            }

            BlockState state = level.getBlockState(bhr.getBlockPos());
            if (!APGProjectileEntity.isGlassLike(state)) {
               blockHit = bhr.getPos();
               blockHitState = state;
               break;
            }

            level.breakBlock(bhr.getBlockPos(), false, player);
            level.spawnParticles(ParticleTypes.CRIT, bhr.getPos().x, bhr.getPos().y, bhr.getPos().z, 4, 0.1, 0.1, 0.1, 0.02);
            segStart = bhr.getPos().add(lookDir.multiply(0.1));
            if (segStart.squaredDistanceTo(var17) >= end.squaredDistanceTo(var17)) {
               blockHit = end;
               break;
            }
         }

         EntityHitResult ehr = ProjectileUtil.getEntityCollision(
            level,
            player,
            var17,
            blockHit,
            new Box(var17, blockHit).expand(1.0),
            e -> e != player && e != player.getVehicle() && e.isAlive() && e.canHit() && !e.isSpectator()
         );
         Vec3d impact;
         if (ehr != null) {
            Entity target = ehr.getEntity();
            impact = ehr.getPos();
            APGProjectileEntity src = new APGProjectileEntity(level, player);
            target.damage(player.getDamageSources().arrow(src, player), APGProjectileEntity.damage());
            APGProjectileEntity.spawnEntityImpact(level, impact, player);
         } else {
            impact = blockHit;
            if (blockHitState != null) {
               APGProjectileEntity.spawnBlockImpact(level, blockHit, blockHitState, player);
            }
         }

         APGProjectileEntity.spawnTracer(level, muzzle, impact);
      }
   }

   public static boolean isFlareGunOnCooldown(ServerPlayerEntity player) {
      Long readyAt = flareGunCooldowns.get(player.getUuid());
      return readyAt == null ? false : player.getServer().getTicks() < readyAt;
   }

   public static void setFlareGunCooldown(ServerPlayerEntity player, int ticks) {
      flareGunCooldowns.put(player.getUuid(), (long)(player.getServer().getTicks() + ticks));
   }

   private static void handleFlareGunLoad(ServerPlayerEntity player) {
      if (!isFlareGunOnCooldown(player)) {
         ItemStack gunStack = null;
         int gunSlot = -1;
         if (player.getMainHandStack().getItem() instanceof FlareGunItem) {
            gunStack = player.getMainHandStack();
            gunSlot = player.getInventory().selectedSlot;
         } else if (player.getOffHandStack().getItem() instanceof FlareGunItem) {
            gunStack = player.getOffHandStack();
            gunSlot = 40;
         }

         if (gunStack != null) {
            if (FlareGunItem.isLoaded(gunStack)) {
               FlareCartridgeItem.FlareColor color = FlareGunItem.getLoadedColor(gunStack);
               if (color != null) {
                  ItemStack returnStack = new ItemStack(FlareCartridgeItem.getItemForColor(color));
                  if (!player.getInventory().insertStack(returnStack)) {
                     player.dropItem(returnStack, false);
                  }
               }

               player.getWorld()
                  .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_END, SoundCategory.PLAYERS, 1.0F, 0.8F);
               if (player.getWorld() instanceof ServerWorld serverLevel) {
                  FlareGunItem flareGunItem = (FlareGunItem)gunStack.getItem();
                  flareGunItem.triggerAnim(player, GeoItem.getOrAssignId(gunStack, serverLevel), "flare_gun_controller", "discard_cartridge");
               }

               setFlareGunCooldown(player, 23);
               FlareGunItem.clearLoaded(gunStack);
            } else {
               int cartridgeSlot = FlareGunItem.findNearestCartridgeSlot(player, gunSlot);
               if (cartridgeSlot < 0) {
                  player.sendMessage(Text.literal("No cartridge available").formatted(Formatting.RED), true);
                  return;
               }

               ItemStack cartridgeStack;
               if (cartridgeSlot == 40) {
                  cartridgeStack = player.getOffHandStack();
               } else {
                  cartridgeStack = player.getInventory().getStack(cartridgeSlot);
               }

               if (!(cartridgeStack.getItem() instanceof FlareCartridgeItem fc)) {
                  return;
               }

               FlareCartridgeItem.FlareColor var14 = fc.getColor();
               if (!player.isCreative()) {
                  cartridgeStack.decrement(1);
               }

               player.getWorld()
                  .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_START, SoundCategory.PLAYERS, 1.0F, 1.0F);
               if (player.getWorld() instanceof ServerWorld serverLevel) {
                  FlareGunItem flareGunItem = (FlareGunItem)gunStack.getItem();
                  flareGunItem.triggerAnim(player, GeoItem.getOrAssignId(gunStack, serverLevel), "flare_gun_controller", "load_cartridge");
               }

               setFlareGunCooldown(player, 23);
               FlareGunItem.setLoadedColorOrdinal(gunStack, var14.ordinal());
            }
         }
      }
   }

   private static void handleFlareGunCycle(ServerPlayerEntity player) {
      if (!isFlareGunOnCooldown(player)) {
         ItemStack gunStack = null;
         int gunSlot = -1;
         if (player.getMainHandStack().getItem() instanceof FlareGunItem) {
            gunStack = player.getMainHandStack();
            gunSlot = player.getInventory().selectedSlot;
         } else if (player.getOffHandStack().getItem() instanceof FlareGunItem) {
            gunStack = player.getOffHandStack();
            gunSlot = 40;
         }

         if (gunStack != null && FlareGunItem.isLoaded(gunStack)) {
            FlareCartridgeItem.FlareColor currentColor = FlareGunItem.getLoadedColor(gunStack);
            if (currentColor != null) {
               FlareCartridgeItem.FlareColor[] colors = FlareCartridgeItem.FlareColor.values();
               int startOrd = currentColor.ordinal();
               int searchSlot = gunSlot == 40 ? player.getInventory().selectedSlot : gunSlot;

               for (int i = 1; i < colors.length; i++) {
                  int nextOrd = (startOrd + i) % colors.length;
                  FlareCartridgeItem.FlareColor nextColor = colors[nextOrd];
                  int slot = FlareGunItem.findCartridgeSlotOfColor(player, searchSlot, nextColor);
                  if (slot >= 0) {
                     ItemStack returnStack = new ItemStack(FlareCartridgeItem.getItemForColor(currentColor));
                     if (!player.getInventory().insertStack(returnStack)) {
                        player.dropItem(returnStack, false);
                     }

                     ItemStack cartridgeStack;
                     if (slot == 40) {
                        cartridgeStack = player.getOffHandStack();
                     } else {
                        cartridgeStack = player.getInventory().getStack(slot);
                     }

                     if (!player.isCreative()) {
                        cartridgeStack.decrement(1);
                     }

                     String colorName = nextColor.name().charAt(0) + nextColor.name().substring(1).toLowerCase();
                     Formatting colorFmt = FlareGunItem.getColorFormatting(nextColor);
                     player.sendMessage(
                        Text.literal("Switched to: ").formatted(Formatting.WHITE).append(Text.literal(colorName).formatted(colorFmt, Formatting.BOLD)), true
                     );
                     player.getWorld()
                        .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_END, SoundCategory.PLAYERS, 1.0F, 1.2F);
                     if (player.getWorld() instanceof ServerWorld serverLevel) {
                        FlareGunItem flareGunItem = (FlareGunItem)gunStack.getItem();
                        flareGunItem.triggerAnim(player, GeoItem.getOrAssignId(gunStack, serverLevel), "flare_gun_controller", "swap_cartridge");
                     }

                     setFlareGunCooldown(player, 15);
                     FlareGunItem.setLoadedColorOrdinal(gunStack, nextOrd);
                     return;
                  }
               }

               player.sendMessage(Text.literal("No other cartridge to swap to").formatted(Formatting.RED), true);
            }
         }
      }
   }

   public static void broadcastBladeAnim(ServerPlayerEntity source, int action) {
      BladeAnimSyncPayload payload = new BladeAnimSyncPayload(source.getId(), action);

      for (ServerPlayerEntity tracker : new HashSet<>(PlayerLookup.tracking(source))) {
         if (tracker != source) {
            ServerPlayNetworking.send(tracker, payload);
         }
      }
   }

   public static void broadcastAwakenToggle(ServerPlayerEntity source, boolean active) {
      AwakenToggleSyncPayload payload = new AwakenToggleSyncPayload(source.getUuid(), active);
      ServerPlayNetworking.send(source, payload);

      for (ServerPlayerEntity tracker : PlayerLookup.tracking(source)) {
         ServerPlayNetworking.send(tracker, payload);
      }
   }

   private record AscendingPlayer(Entity titan, int ticksElapsed, ModNetworking.ShifterType shifterType) {
   }

   private record FounderTarget(UUID targetUUID, String ownerName) {
   }

   private record PendingBite(int ticksRemaining, ModNetworking.ShifterType shifterType, boolean tease) {
   }

   private record PendingCommand(UUID playerUUID, String command, int executeTick, Vec3d position, float yaw, boolean crouching) {
      PendingCommand(UUID playerUUID, String command, int executeTick) {
         this(playerUUID, command, executeTick, null, 0.0F, false);
      }

      PendingCommand(UUID playerUUID, String command, int executeTick, Vec3d position, float yaw) {
         this(playerUUID, command, executeTick, position, yaw, false);
      }
   }

   private record PendingShift(double x, double y, double z, float yaw, int ticksRemaining, ModNetworking.ShifterType shifterType) {
   }

   public static enum ShifterType {
      COLOSSAL,
      ATTACK,
      WARHAMMER,
      ARMORED,
      BEAST,
      FEMALE,
      PURE_TITAN,
      FOUNDING,
      TRIPLE_T,
      OGRE_SHIFTER,
      JAW,
      CART;
   }

   private static enum TargetMode {
      NONE,
      SELECTING,
      ACTIVE;
   }
}
