package daot;

import daot.network.ModNetworking;
import daot.network.VillagerTransformPayload;
import daot.network.VillagerTransformSpawnPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import daot.compat.DamageEvents.AfterDamage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class VillagerTransformTracker {
   private static final String INJECTED_TAG = "dannysaot_injected";
   private static final String INJECTOR_PREFIX = "dannysaot_injector:";
   private static final String ROYAL_FLUID_TAG = "dannysaot_royal_fluid";
   public static final String ROAR_OWNER_PREFIX = "dannysaot_roar_owner:";
   private static final Map<UUID, VillagerTransformTracker.RegroupTarget> regroupTargets = new HashMap<>();
   private static final Map<String, VillagerTransformTracker.ActiveTarget> activeTargets = new HashMap<>();
   public static final String FEMALE_TITAN_ROAR_PREFIX = "dannysaot_femaletitan_roar:";
   private static final String PREV_ROAR_OWNER_PREFIX = "dannysaot_prev_roar_owner:";
   private static final Map<UUID, VillagerTransformTracker.PendingTransformation> pendingTransforms = new HashMap<>();
   public static final int PRESHIFT_TICKS = 40;
   public static final float DAMAGE_THRESHOLD = 3.0F;
   private static boolean initialized = false;

   public static void setRegroupTarget(Entity entity, double x, double z, float facingYaw) {
      regroupTargets.put(entity.getUuid(), new VillagerTransformTracker.RegroupTarget(x, z, facingYaw));
   }

   public static VillagerTransformTracker.RegroupTarget getRegroupTarget(Entity entity) {
      return regroupTargets.get(entity.getUuid());
   }

   public static boolean isRegrouping(Entity entity) {
      return regroupTargets.containsKey(entity.getUuid());
   }

   public static void clearRegroup(Entity entity) {
      regroupTargets.remove(entity.getUuid());
   }

   public static void setActiveTarget(String ownerName, UUID targetUUID, String targetName) {
      activeTargets.put(ownerName, new VillagerTransformTracker.ActiveTarget(targetUUID, targetName));
   }

   public static boolean hasActiveTarget(String ownerName) {
      return ownerName != null && activeTargets.containsKey(ownerName);
   }

   public static VillagerTransformTracker.ActiveTarget getActiveTarget(String ownerName) {
      return activeTargets.get(ownerName);
   }

   public static void clearActiveTarget(String ownerName) {
      activeTargets.remove(ownerName);
   }

   private static String roarOwnerKey(FemaleTitanEntity femaleTitan) {
      return "__femaletitanroar_" + femaleTitan.getId();
   }

   public static void applyFemaleTitanRoar(FemaleTitanEntity femaleTitan, double radius) {
      if (!femaleTitan.getWorld().isClient()) {
         String syntheticOwner = roarOwnerKey(femaleTitan);
         String roarMarker = "dannysaot_femaletitan_roar:" + femaleTitan.getId();
         Box box = femaleTitan.getBoundingBox().expand(radius);

         for (HostileEntity titan : femaleTitan.getWorld()
            .getEntitiesByClass(HostileEntity.class, box, m -> m != femaleTitan && isCommandablePureTitan(m) && m.distanceTo(femaleTitan) <= radius)) {
            cancelTitanEating(titan);
            titan.removeScoreboardTag("dannysaot_commanded_stop");
            String existingOwner = getOwnerName(titan);
            if (existingOwner != null && !existingOwner.equals(syntheticOwner)) {
               titan.removeScoreboardTag("dannysaot_roar_owner:" + existingOwner);
               titan.addCommandTag("dannysaot_prev_roar_owner:" + existingOwner);
            }

            titan.addCommandTag("dannysaot_roar_owner:" + syntheticOwner);
            titan.addCommandTag(roarMarker);
         }

         setActiveTarget(syntheticOwner, femaleTitan.getUuid(), "Female Titan");
      }
   }

   public static void clearFemaleTitanRoar(FemaleTitanEntity femaleTitan) {
      if (!femaleTitan.getWorld().isClient()) {
         String syntheticOwner = roarOwnerKey(femaleTitan);
         String roarMarker = "dannysaot_femaletitan_roar:" + femaleTitan.getId();
         Box box = femaleTitan.getBoundingBox().expand(512.0);

         for (HostileEntity titan : femaleTitan.getWorld().getEntitiesByClass(HostileEntity.class, box, m -> m.getCommandTags().contains(roarMarker))) {
            titan.removeScoreboardTag(roarMarker);
            titan.removeScoreboardTag("dannysaot_roar_owner:" + syntheticOwner);
            String prevOwner = null;

            for (String tag : titan.getCommandTags()) {
               if (tag.startsWith("dannysaot_prev_roar_owner:")) {
                  prevOwner = tag.substring("dannysaot_prev_roar_owner:".length());
                  break;
               }
            }

            if (prevOwner != null) {
               titan.removeScoreboardTag("dannysaot_prev_roar_owner:" + prevOwner);
               titan.addCommandTag("dannysaot_roar_owner:" + prevOwner);
            }

            if (titan instanceof LivingEntity && titan.getAttacker() == null) {
               titan.setTarget(null);
            }
         }

         clearActiveTarget(syntheticOwner);
      }
   }

   public static boolean isShifterTitan(Entity entity) {
      return entity instanceof FemaleTitanEntity
         || entity instanceof AttackTitanEntity
         || entity instanceof ArmoredTitanEntity
         || entity instanceof ColossalTitanEntity
         || entity instanceof BeastTitanEntity
         || entity instanceof WarhammerTitanEntity;
   }

   public static boolean isFromShifter(DamageSource source) {
      return source == null ? false : isShifterEntity(source.getAttacker()) || isShifterEntity(source.getSource());
   }

   private static boolean isShifterEntity(Entity entity) {
      return entity == null ? false : isShifterTitan(entity) || isShifterTitan(entity.getVehicle());
   }

   private static boolean isCommandablePureTitan(Entity entity) {
      return entity instanceof SmallTitanEntity
         || entity instanceof SmallTitan2Entity
         || entity instanceof TitanEntity
         || entity instanceof FritzTitanEntity
         || entity instanceof ConnieFatherEntity
         || entity instanceof SadTitanEntity
         || entity instanceof YellowTitanEntity
         || entity instanceof OgreTitanEntity;
   }

   private static void cancelTitanEating(Entity entity) {
      if (entity instanceof SmallTitanEntity t) {
         t.cancelEating();
      } else if (entity instanceof SmallTitan2Entity t) {
         t.cancelEating();
      } else if (entity instanceof TitanEntity t) {
         t.cancelEating();
      } else if (entity instanceof FritzTitanEntity t) {
         t.cancelEating();
      } else if (entity instanceof ConnieFatherEntity t) {
         t.cancelEating();
      } else if (entity instanceof SadTitanEntity t) {
         t.cancelEating();
      } else if (entity instanceof YellowTitanEntity t) {
         t.cancelEating();
      } else if (entity instanceof OgreTitanEntity t) {
         t.cancelEating();
      }
   }

   public static String getOwnerName(Entity entity) {
      for (String tag : entity.getCommandTags()) {
         if (tag.startsWith("dannysaot_roar_owner:")) {
            return tag.substring("dannysaot_roar_owner:".length());
         }
      }

      return null;
   }

   public static void initialize() {
      if (!initialized) {
         initialized = true;
         ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> tickPendingTransformations());
         daot.compat.DamageEvents.AFTER_DAMAGE.register((AfterDamage)(entity, source, baseDamage, damageTaken, blocked) -> {
            if (entity instanceof VillagerEntity villager) {
               if (isInjected(villager) && !isTransforming(villager)) {
                  if (damageTaken >= 3.0F) {
                     ServerWorld level = (ServerWorld)villager.getWorld();
                     clearInjectionTags(villager);
                     startTransformation(villager, level);
                     VillagerTransformPayload payload = new VillagerTransformPayload(villager.getId());

                     for (ServerPlayerEntity nearby : PlayerLookup.tracking(villager)) {
                        ServerPlayNetworking.send(nearby, payload);
                     }

                     DannysAot.LOGGER.info("Injected villager took {} damage, starting transformation", damageTaken);
                  }
               }
            } else {
               if (entity instanceof ServerPlayerEntity targetPlayer) {
                  if (!isPlayerInjected(targetPlayer)) {
                     return;
                  }

                  if (damageTaken >= 3.0F) {
                     BloodlineData bloodlineData = BloodlineData.get(targetPlayer.getServerWorld());
                     BloodlineType bloodline = bloodlineData.getBloodline(targetPlayer.getUuid());
                     if (bloodline == BloodlineType.ACKERMAN || bloodline == BloodlineType.MARLEYAN) {
                        return;
                     }

                     ServerWorld level = targetPlayer.getServerWorld();
                     clearInjectionTags(targetPlayer);
                     ModNetworking.initiatePureTitanShift(targetPlayer, level);
                     DannysAot.LOGGER.info("Injected player {} took {} damage, transforming into pure titan", targetPlayer.getName().getString(), damageTaken);
                  }
               }
            }
         });
         DannysAot.LOGGER.info("VillagerTransformTracker initialized");
      }
   }

   private static void setInjectionTags(Entity entity, String injectorName, boolean isRoyal) {
      entity.addCommandTag("dannysaot_injected");
      entity.addCommandTag("dannysaot_injector:" + injectorName);
      if (isRoyal) {
         entity.addCommandTag("dannysaot_royal_fluid");
      }
   }

   private static void clearInjectionTags(Entity entity) {
      entity.removeScoreboardTag("dannysaot_injected");
      entity.removeScoreboardTag("dannysaot_royal_fluid");
      String injectorTag = null;

      for (String tag : entity.getCommandTags()) {
         if (tag.startsWith("dannysaot_injector:")) {
            injectorTag = tag;
            break;
         }
      }

      if (injectorTag != null) {
         entity.removeScoreboardTag(injectorTag);
      }
   }

   private static String getInjectorName(Entity entity) {
      for (String tag : entity.getCommandTags()) {
         if (tag.startsWith("dannysaot_injector:")) {
            return tag.substring("dannysaot_injector:".length());
         }
      }

      return null;
   }

   private static boolean hasRoyalFluid(Entity entity) {
      return entity.getCommandTags().contains("dannysaot_royal_fluid");
   }

   public static void injectVillager(VillagerEntity villager, String injectorName, boolean isRoyal) {
      setInjectionTags(villager, injectorName, isRoyal);
      DannysAot.LOGGER
         .info(
            "Villager injected at ({}, {}, {}) with {}'s spinal fluid (royal={})",
            new Object[]{villager.getX(), villager.getY(), villager.getZ(), injectorName, isRoyal}
         );
   }

   public static boolean isInjected(VillagerEntity villager) {
      return villager.getCommandTags().contains("dannysaot_injected");
   }

   public static String getVillagerInjectorName(VillagerEntity villager) {
      return getInjectorName(villager);
   }

   public static boolean isVillagerRoyalFluid(VillagerEntity villager) {
      return hasRoyalFluid(villager);
   }

   public static boolean isTransforming(VillagerEntity villager) {
      return pendingTransforms.containsKey(villager.getUuid());
   }

   public static void injectPlayer(ServerPlayerEntity player, String injectorName, boolean isRoyal) {
      setInjectionTags(player, injectorName, isRoyal);
      DannysAot.LOGGER.info("Player {} injected with {}'s spinal fluid (royal={})", new Object[]{player.getName().getString(), injectorName, isRoyal});
   }

   public static boolean isPlayerInjected(PlayerEntity player) {
      return player.getCommandTags().contains("dannysaot_injected");
   }

   public static String getPlayerInjectorName(PlayerEntity player) {
      return getInjectorName(player);
   }

   public static boolean isPlayerRoyalFluid(PlayerEntity player) {
      return hasRoyalFluid(player);
   }

   public static void removeInjectedPlayer(ServerPlayerEntity player) {
      clearInjectionTags(player);
   }

   public static void removeInjectedPlayer(UUID playerUUID, MinecraftServer server) {
      if (server != null) {
         ServerPlayerEntity target = server.getPlayerManager().getPlayer(playerUUID);
         if (target != null) {
            clearInjectionTags(target);
         }
      }
   }

   public static void startTransformation(VillagerEntity villager, ServerWorld level) {
      startTransformation(villager, level, 40);
   }

   public static void startTransformation(VillagerEntity villager, ServerWorld level, int delayTicks) {
      BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
      double px = villager.getX();
      double py = villager.getY() + villager.getHeight() * 0.5;
      double pz = villager.getZ();
      level.spawnParticles(bloodParticle, px, py, pz, 30, 0.3, 0.4, 0.3, 0.1);
      level.spawnParticles(bloodParticle, px, py + 0.3, pz, 20, 0.1, 0.1, 0.1, 0.15);
      pendingTransforms.put(villager.getUuid(), new VillagerTransformTracker.PendingTransformation(villager, level, delayTicks));
      DannysAot.LOGGER
         .info(
            "Started villager transformation at ({}, {}, {}) with {} tick delay", new Object[]{villager.getX(), villager.getY(), villager.getZ(), delayTicks}
         );
   }

   private static void tickPendingTransformations() {
      Iterator<Entry<UUID, VillagerTransformTracker.PendingTransformation>> iterator = pendingTransforms.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, VillagerTransformTracker.PendingTransformation> entry = iterator.next();
         VillagerTransformTracker.PendingTransformation transform = entry.getValue();
         VillagerEntity villager = transform.villager();
         ServerWorld level = transform.level();
         if (villager != null && !villager.isRemoved() && villager.isAlive()) {
            if (transform.ticksRemaining() <= 1) {
               spawnTitanFromVillager(villager, level);
               iterator.remove();
            } else {
               pendingTransforms.put(entry.getKey(), new VillagerTransformTracker.PendingTransformation(villager, level, transform.ticksRemaining() - 1));
            }
         } else {
            iterator.remove();
         }
      }
   }

   private static void spawnTitanFromVillager(VillagerEntity villager, ServerWorld level) {
      double x = villager.getX();
      double y = villager.getY();
      double z = villager.getZ();
      float yaw = villager.getYaw();
      String injectorName = getInjectorName(villager);
      spawnShiftPureTitan(level, x, y, z, yaw, injectorName, false);
      villager.discard();
   }

   public static HostileEntity spawnShiftPureTitan(ServerWorld level, double x, double y, double z, float yaw, String ownerName, boolean excludeCrawler) {
      ModNetworking.spawnShiftLightning(level, x, y, z);
      BlockPos diffPos = BlockPos.ofFloored(x, y, z);
      int roll = level.random.nextInt(23);
      if (excludeCrawler) {
         while (roll == 20 || roll == 21) {
            roll = level.random.nextInt(23);
         }
      }

      HostileEntity titan;
      String titanType;
      if (roll <= 1) {
         SmallTitanEntity t = new SmallTitanEntity(DannysAot.SMALL_TITAN, level);
         t.setPosition(x, y, z);
         t.setYaw(yaw);
         t.bodyYaw = yaw;
         t.setHeadYaw(yaw);
         t.initialize(level, level.getLocalDifficulty(diffPos), SpawnReason.CONVERSION, null, null);
         titan = t;
         titanType = "Small Titan";
      } else if (roll <= 3) {
         SmallTitan2Entity t = new SmallTitan2Entity(DannysAot.SMALL_TITAN_2, level);
         t.setPosition(x, y, z);
         t.setYaw(yaw);
         t.bodyYaw = yaw;
         t.setHeadYaw(yaw);
         t.initialize(level, level.getLocalDifficulty(diffPos), SpawnReason.CONVERSION, null, null);
         titan = t;
         titanType = "Small Titan 2";
      } else if (roll <= 5) {
         FritzTitanEntity t = new FritzTitanEntity(DannysAot.FRITZ_TITAN, level);
         t.setPosition(x, y, z);
         t.setYaw(yaw);
         t.bodyYaw = yaw;
         t.setHeadYaw(yaw);
         t.initialize(level, level.getLocalDifficulty(diffPos), SpawnReason.CONVERSION, null, null);
         titan = t;
         titanType = "Fritz Titan";
      } else if (roll <= 7) {
         TitanBeardEntity t = new TitanBeardEntity(DannysAot.TITAN_BEARD, level);
         t.setPosition(x, y, z);
         t.setYaw(yaw);
         t.bodyYaw = yaw;
         t.setHeadYaw(yaw);
         t.initialize(level, level.getLocalDifficulty(diffPos), SpawnReason.CONVERSION, null, null);
         titan = t;
         titanType = "Titan Beard";
      } else if (roll <= 9) {
         AbnormalTitanEntity t = new AbnormalTitanEntity(DannysAot.ABNORMAL_TITAN, level);
         t.setPosition(x, y, z);
         t.setYaw(yaw);
         t.bodyYaw = yaw;
         t.setHeadYaw(yaw);
         t.initialize(level, level.getLocalDifficulty(diffPos), SpawnReason.CONVERSION, null, null);
         titan = t;
         titanType = "Abnormal";
      } else if (roll <= 11) {
         ConnieFatherEntity t = new ConnieFatherEntity(DannysAot.CONNIE_FATHER, level);
         t.setPosition(x, y, z);
         t.setYaw(yaw);
         t.bodyYaw = yaw;
         t.setHeadYaw(yaw);
         t.initialize(level, level.getLocalDifficulty(diffPos), SpawnReason.CONVERSION, null, null);
         titan = t;
         titanType = "Connie's Father";
      } else if (roll <= 13) {
         TitanEntity t = new TitanEntity(DannysAot.TITAN, level);
         t.setPosition(x, y, z);
         t.setYaw(yaw);
         t.bodyYaw = yaw;
         t.setHeadYaw(yaw);
         t.initialize(level, level.getLocalDifficulty(diffPos), SpawnReason.CONVERSION, null, null);
         titan = t;
         titanType = "Titan";
      } else if (roll <= 15) {
         TitanTropicalEntity t = new TitanTropicalEntity(DannysAot.TITAN_TROPICAL, level);
         t.setPosition(x, y, z);
         t.setYaw(yaw);
         t.bodyYaw = yaw;
         t.setHeadYaw(yaw);
         t.initialize(level, level.getLocalDifficulty(diffPos), SpawnReason.CONVERSION, null, null);
         titan = t;
         titanType = "Tropical Titan";
      } else if (roll <= 17) {
         YellowTitanEntity t = new YellowTitanEntity(DannysAot.YELLOW_TITAN, level);
         t.setPosition(x, y, z);
         t.setYaw(yaw);
         t.bodyYaw = yaw;
         t.setHeadYaw(yaw);
         t.initialize(level, level.getLocalDifficulty(diffPos), SpawnReason.CONVERSION, null, null);
         titan = t;
         titanType = "Yellow Titan";
      } else if (roll <= 19) {
         SadTitanEntity t = new SadTitanEntity(DannysAot.SAD_TITAN, level);
         t.setPosition(x, y, z);
         t.setYaw(yaw);
         t.bodyYaw = yaw;
         t.setHeadYaw(yaw);
         t.initialize(level, level.getLocalDifficulty(diffPos), SpawnReason.CONVERSION, null, null);
         titan = t;
         titanType = "Sad Titan";
      } else if (roll <= 21) {
         CrawlerTitanEntity t = new CrawlerTitanEntity(DannysAot.CRAWLER_TITAN, level);
         t.setPosition(x, y, z);
         t.setYaw(yaw);
         t.bodyYaw = yaw;
         t.setHeadYaw(yaw);
         t.initialize(level, level.getLocalDifficulty(diffPos), SpawnReason.CONVERSION, null, null);
         titan = t;
         titanType = "Crawler Titan";
      } else {
         OgreTitanEntity t = new OgreTitanEntity(DannysAot.OGRE_TITAN, level);
         t.setPosition(x, y, z);
         t.setYaw(yaw);
         t.bodyYaw = yaw;
         t.setHeadYaw(yaw);
         t.initialize(level, level.getLocalDifficulty(diffPos), SpawnReason.CONVERSION, null, null);
         titan = t;
         titanType = "Ogre Titan";
      }

      titan.prevYaw = yaw;
      if (ownerName != null) {
         titan.addCommandTag("dannysaot_roar_owner:" + ownerName);
      }

      level.spawnEntity(titan);
      VillagerTransformSpawnPayload spawnPayload = new VillagerTransformSpawnPayload(x, y, z, titan.getId());

      for (ServerPlayerEntity nearby : PlayerLookup.tracking(level, diffPos)) {
         ServerPlayNetworking.send(nearby, spawnPayload);
      }

      DannysAot.LOGGER.info("Shift-spawned pure titan {} at ({}, {}, {})", new Object[]{titanType, x, y, z});
      return titan;
   }

   public record ActiveTarget(UUID targetUUID, String targetName) {
   }

   private record PendingTransformation(VillagerEntity villager, ServerWorld level, int ticksRemaining) {
   }

   public record RegroupTarget(double x, double z, float facingYaw) {
   }
}
