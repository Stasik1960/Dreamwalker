package daot;

import daot.advancement.ModCriteriaTriggers;
import daot.network.PoweredVillagerSyncPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;

public class TitanPowerHelper {
   private static final RegistryKey<World> PATHS_DIMENSION = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paths"));
   private static final Map<UUID, TitanPowerHelper.PathsReturnData> pendingPathsReturns = new HashMap<>();

   public static void handleEatingCompletion(HostileEntity titan, LivingEntity target, ServerWorld level) {
      if (level.getGameRules().getBoolean(DannysAot.RULE_TITAN_GORE)) {
         Vec3d kick = titan.getRotationVector();
         GibEntity.spawnGibs(level, target, kick, 1.0F);
      }

      ServerPlayerEntity controllingPlayer = getControllingPlayer(titan);
      TitanPowerType eatenPower = null;
      if (target instanceof VillagerEntity villager) {
         TitanPowerData data = TitanPowerData.get(level);
         TitanPowerType villagerPower = data.getPower(villager.getUuid());
         if (!villager.isDead() && villagerPower != null) {
            RegistryKey<DamageType> eatDamageKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("dannys-aot", "titan_devour"));
            RegistryEntry<DamageType> eatDamageHolder = level.getRegistryManager().getWrapperOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(eatDamageKey);
            DamageSource lethalSource = new DamageSource(eatDamageHolder, titan);
            villager.timeUntilRegen = 0;
            villager.damage(lethalSource, Float.MAX_VALUE);
         }

         if (!villager.isDead()) {
            return;
         }

         if (villagerPower != null) {
            eatenPower = villagerPower;
            data.removePower(villager.getUuid());
            broadcastPowerRemoval(level, villager.getId());
         }
      }

      if (target instanceof ServerPlayerEntity player) {
         if (!player.isDead() && level.getGameRules().getBoolean(DannysAot.RULE_CONFIRMED_TITAN_SHIFTER_KILL)) {
            RegistryKey<DamageType> eatDamageKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("dannys-aot", "titan_devour"));
            RegistryEntry<DamageType> eatDamageHolder = level.getRegistryManager().getWrapperOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(eatDamageKey);
            DamageSource lethalSource = new DamageSource(eatDamageHolder, titan);
            player.timeUntilRegen = 0;
            player.damage(lethalSource, Float.MAX_VALUE);
         }

         if (!player.isDead()) {
            return;
         }

         TitanPowerData datax = TitanPowerData.get(level);
         if (player.getCommandTags().contains("warhammer")) {
            eatenPower = TitanPowerType.WARHAMMER;
            player.removeScoreboardTag("warhammer");
            datax.removePlayerFromPower(player.getUuid(), TitanPowerType.WARHAMMER);
         } else if (player.getCommandTags().contains("attack")) {
            eatenPower = TitanPowerType.ATTACK;
            player.removeScoreboardTag("attack");
            datax.removePlayerFromPower(player.getUuid(), TitanPowerType.ATTACK);
         } else if (player.getCommandTags().contains("colossal")) {
            eatenPower = TitanPowerType.COLOSSAL;
            player.removeScoreboardTag("colossal");
            datax.removePlayerFromPower(player.getUuid(), TitanPowerType.COLOSSAL);
         } else if (player.getCommandTags().contains("armored")) {
            eatenPower = TitanPowerType.ARMORED;
            player.removeScoreboardTag("armored");
            datax.removePlayerFromPower(player.getUuid(), TitanPowerType.ARMORED);
         } else if (player.getCommandTags().contains("beast")) {
            eatenPower = TitanPowerType.BEAST;
            player.removeScoreboardTag("beast");
            datax.removePlayerFromPower(player.getUuid(), TitanPowerType.BEAST);
         } else if (player.getCommandTags().contains("female")) {
            eatenPower = TitanPowerType.FEMALE;
            player.removeScoreboardTag("female");
            datax.removePlayerFromPower(player.getUuid(), TitanPowerType.FEMALE);
         } else if (player.getCommandTags().contains("jaw")) {
            eatenPower = TitanPowerType.JAW;
            player.removeScoreboardTag("jaw");
            datax.removePlayerFromPower(player.getUuid(), TitanPowerType.JAW);
         }
      }

      if (eatenPower != null) {
         if (target.isAlive()) {
            target.kill();
         }

         if (controllingPlayer != null) {
            grantPowerToPlayer(controllingPlayer, eatenPower, level);
            revertPlayerToHuman(controllingPlayer, titan, level);
         } else {
            transformTitanToVillager(titan, eatenPower, level);
         }
      }
   }

   private static ServerPlayerEntity getControllingPlayer(HostileEntity titan) {
      for (Entity passenger : titan.getPassengerList()) {
         if (passenger instanceof ServerPlayerEntity player) {
            if (titan instanceof TitanEntity t && t.isSelfInjectionPassenger(player)) {
               return player;
            }

            if (titan instanceof SmallTitanEntity st && st.isSelfInjectionPassenger(player)) {
               return player;
            }

            if (titan instanceof SmallTitan2Entity st2 && st2.isSelfInjectionPassenger(player)) {
               return player;
            }

            if (titan instanceof FritzTitanEntity ft && ft.isSelfInjectionPassenger(player)) {
               return player;
            }

            if (titan instanceof YellowTitanEntity yt && yt.isSelfInjectionPassenger(player)) {
               return player;
            }

            if (titan instanceof SadTitanEntity sad && sad.isSelfInjectionPassenger(player)) {
               return player;
            }

            if (titan instanceof CrawlerTitanEntity cr && cr.isSelfInjectionPassenger(player)) {
               return player;
            }

            if (titan instanceof ConnieFatherEntity cf && cf.isSelfInjectionPassenger(player)) {
               return player;
            }

            if (titan instanceof OgreTitanEntity ot && ot.isSelfInjectionPassenger(player)) {
               return player;
            }
         }
      }

      return null;
   }

   public static boolean isPureTitan(Entity entity) {
      return entity instanceof TitanEntity
         || entity instanceof SmallTitanEntity
         || entity instanceof SmallTitan2Entity
         || entity instanceof FritzTitanEntity
         || entity instanceof YellowTitanEntity
         || entity instanceof SadTitanEntity
         || entity instanceof CrawlerTitanEntity
         || entity instanceof ConnieFatherEntity
         || entity instanceof OgreTitanEntity;
   }

   private static void grantPowerToPlayer(ServerPlayerEntity player, TitanPowerType power, ServerWorld level) {
      TitanPowerData data = TitanPowerData.get(level);
      List<UUID> villagersToRemove = data.getVillagersWithPower(power);
      boolean multi = level.getGameRules().getBoolean(DannysAot.RULE_MULTIPLE_SHIFTERS);
      GrantProvenance earned = GrantProvenance.ofInheritance(level.getServer());
      if (multi) {
         data.addPlayerToPower(player.getUuid(), power, earned);
      } else {
         data.setPlayerPower(player.getUuid(), power, earned);
      }

      String tag = power.getTagName();
      player.addCommandTag(tag);

      for (UUID villagerUUID : villagersToRemove) {
         Entity entity = level.getEntity(villagerUUID);
         if (entity != null) {
            broadcastPowerRemoval(level, entity.getId());
         }
      }

      for (ServerWorld sl : level.getServer().getWorlds()) {
         TitanPowerData allData = TitanPowerData.get(sl);

         for (Entry<UUID, TitanPowerType> entry : allData.getPowerMap().entrySet()) {
            if (entry.getValue() == power) {
               Entity entity = sl.getEntity(entry.getKey());
               if (entity != null) {
                  broadcastPowerRemoval(sl, entity.getId());
               }
            }
         }
      }

      player.sendMessage(Text.literal("You inherited the " + power.getDisplayName() + " power!").styled(style -> {
         return style.withColor(switch (power) {
            case ATTACK -> 5635925;
            case COLOSSAL -> 16733525;
            case ARMORED -> 16753920;
            case BEAST -> 9132327;
            case FEMALE -> 16738740;
            case WARHAMMER -> 8388736;
            case JAW -> 16753920;
         });
      }), false);
      ModCriteriaTriggers.INHERITED_TITAN_POWER.trigger(player);
      broadcastShifterClaimed(level, power);
      DannysAot.LOGGER
         .info(
            "Player {} acquired {} power - removed from {} villagers",
            new Object[]{player.getName().getString(), power.getDisplayName(), villagersToRemove.size()}
         );
   }

   public static void handlePlayerPowerLoss(ServerPlayerEntity player, ServerWorld level) {
      TitanPowerData data = TitanPowerData.get(level);
      UUID uuid = player.getUuid();
      if (player.getCommandTags().contains("attack")) {
         player.removeScoreboardTag("attack");
         data.removePlayerFromPower(uuid, TitanPowerType.ATTACK);
         if (!data.playerHasPower(TitanPowerType.ATTACK)) {
            broadcastShifterUnclaimed(level, TitanPowerType.ATTACK);
         }

         DannysAot.LOGGER.info("Player {} lost Attack Titan power (death)", player.getName().getString());
      }

      if (player.getCommandTags().contains("colossal")) {
         player.removeScoreboardTag("colossal");
         data.removePlayerFromPower(uuid, TitanPowerType.COLOSSAL);
         if (!data.playerHasPower(TitanPowerType.COLOSSAL)) {
            broadcastShifterUnclaimed(level, TitanPowerType.COLOSSAL);
         }

         DannysAot.LOGGER.info("Player {} lost Colossal Titan power (death)", player.getName().getString());
      }

      if (player.getCommandTags().contains("armored")) {
         player.removeScoreboardTag("armored");
         data.removePlayerFromPower(uuid, TitanPowerType.ARMORED);
         if (!data.playerHasPower(TitanPowerType.ARMORED)) {
            broadcastShifterUnclaimed(level, TitanPowerType.ARMORED);
         }

         DannysAot.LOGGER.info("Player {} lost Armored Titan power (death)", player.getName().getString());
      }

      if (player.getCommandTags().contains("beast")) {
         player.removeScoreboardTag("beast");
         data.removePlayerFromPower(uuid, TitanPowerType.BEAST);
         if (!data.playerHasPower(TitanPowerType.BEAST)) {
            broadcastShifterUnclaimed(level, TitanPowerType.BEAST);
         }

         DannysAot.LOGGER.info("Player {} lost Beast Titan power (death)", player.getName().getString());
      }

      if (player.getCommandTags().contains("female")) {
         player.removeScoreboardTag("female");
         data.removePlayerFromPower(uuid, TitanPowerType.FEMALE);
         if (!data.playerHasPower(TitanPowerType.FEMALE)) {
            broadcastShifterUnclaimed(level, TitanPowerType.FEMALE);
         }

         DannysAot.LOGGER.info("Player {} lost Female Titan power (death)", player.getName().getString());
      }

      if (player.getCommandTags().contains("warhammer")) {
         player.removeScoreboardTag("warhammer");
         data.removePlayerFromPower(uuid, TitanPowerType.WARHAMMER);
         if (!data.playerHasPower(TitanPowerType.WARHAMMER)) {
            broadcastShifterUnclaimed(level, TitanPowerType.WARHAMMER);
         }

         DannysAot.LOGGER.info("Player {} lost Warhammer Titan power (death)", player.getName().getString());
      }

      if (player.getCommandTags().contains("jaw")) {
         player.removeScoreboardTag("jaw");
         data.removePlayerFromPower(uuid, TitanPowerType.JAW);
         if (!data.playerHasPower(TitanPowerType.JAW)) {
            broadcastShifterUnclaimed(level, TitanPowerType.JAW);
         }

         DannysAot.LOGGER.info("Player {} lost Jaw Titan power (death)", player.getName().getString());
      }
   }

   public static ServerPlayerEntity getShifterDriver(Entity entity, ServerWorld level) {
      UUID driverUUID;
      if (entity instanceof TestShifterTitanEntity a) {
         driverUUID = a.getShifterUUID();
      } else if (entity instanceof AttackTitanEntity a) {
         driverUUID = a.getShifterUUID();
      } else if (entity instanceof ArmoredTitanEntity a) {
         driverUUID = a.getShifterUUID();
      } else if (entity instanceof FemaleTitanEntity a) {
         driverUUID = a.getShifterUUID();
      } else if (entity instanceof BeastTitanEntity a) {
         driverUUID = a.getShifterUUID();
      } else if (entity instanceof WarhammerTitanEntity a) {
         driverUUID = a.getShifterUUID();
      } else {
         if (!(entity instanceof ColossalTitanEntity a)) {
            return null;
         }

         driverUUID = a.getShifterUUID();
      }

      return driverUUID == null ? null : level.getServer().getPlayerManager().getPlayer(driverUUID);
   }

   public static boolean tryShifterInheritFromVillager(Entity killer, VillagerEntity villager, ServerWorld level) {
      if (killer == null) {
         return false;
      } else {
         ServerPlayerEntity driver = getShifterDriver(killer, level);
         if (driver == null) {
            return false;
         } else {
            TitanPowerData data = TitanPowerData.get(level);
            TitanPowerType villagerPower = data.getPower(villager.getUuid());
            if (villagerPower == null) {
               return false;
            } else {
               data.removePower(villager.getUuid());
               broadcastPowerRemoval(level, villager.getId());
               grantPowerToPlayer(driver, villagerPower, level);
               return true;
            }
         }
      }
   }

   public static void handleVillagerPowerLoss(VillagerEntity villager, ServerWorld level) {
      TitanPowerData data = TitanPowerData.get(level);
      TitanPowerType power = data.getPower(villager.getUuid());
      if (power != null) {
         data.removePower(villager.getUuid());
         broadcastPowerRemoval(level, villager.getId());
         broadcastShifterUnclaimed(level, power);
         DannysAot.LOGGER.info("Villager lost {} power (death)", power.getDisplayName());
      }
   }

   public static void tickPathsReturns(MinecraftServer server) {
      if (!pendingPathsReturns.isEmpty()) {
         long currentTick = server.getTicks();
         Iterator<Entry<UUID, TitanPowerHelper.PathsReturnData>> iterator = pendingPathsReturns.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<UUID, TitanPowerHelper.PathsReturnData> entry = iterator.next();
            TitanPowerHelper.PathsReturnData data = entry.getValue();
            if (currentTick >= data.returnAtTick()) {
               iterator.remove();
               ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
               if (player != null && player.isAlive()) {
                  ServerWorld returnLevel = server.getWorld(data.returnDim());
                  if (returnLevel != null) {
                     player.teleport(returnLevel, data.x(), data.y(), data.z(), player.getYaw(), player.getPitch());
                  }
               }
            }
         }
      }
   }

   private static void revertPlayerToHuman(ServerPlayerEntity player, HostileEntity titan, ServerWorld level) {
      double x = titan.getX();
      double y = titan.getY();
      double z = titan.getZ();
      RegistryKey<World> originDimension = level.getRegistryKey();
      if (titan instanceof TitanEntity t) {
         t.removeSelfInjectionPassenger(player);
      } else if (titan instanceof SmallTitanEntity st) {
         st.removeSelfInjectionPassenger(player);
      } else if (titan instanceof SmallTitan2Entity st2) {
         st2.removeSelfInjectionPassenger(player);
      } else if (titan instanceof FritzTitanEntity ft) {
         ft.removeSelfInjectionPassenger(player);
      } else if (titan instanceof YellowTitanEntity yt) {
         yt.removeSelfInjectionPassenger(player);
      } else if (titan instanceof SadTitanEntity sad) {
         sad.removeSelfInjectionPassenger(player);
      } else if (titan instanceof CrawlerTitanEntity cr) {
         cr.removeSelfInjectionPassenger(player);
      } else if (titan instanceof ConnieFatherEntity cf) {
         cf.removeSelfInjectionPassenger(player);
      } else if (titan instanceof OgreTitanEntity ot) {
         ot.removeSelfInjectionPassenger(player);
      }

      player.setInvisible(false);
      player.stopRiding();
      player.requestTeleport(x, y, z);
      titan.kill();
      ServerWorld pathsLevel = level.getServer().getWorld(PATHS_DIMENSION);
      if (pathsLevel != null) {
         player.teleport(pathsLevel, 0.5, 91.0, 0.5, player.getYaw(), player.getPitch());
         long returnTick = level.getServer().getTicks() + 200;
         pendingPathsReturns.put(player.getUuid(), new TitanPowerHelper.PathsReturnData(originDimension, x, y, z, returnTick));
      }
   }

   private static void transformTitanToVillager(HostileEntity titan, TitanPowerType power, ServerWorld level) {
      double x = titan.getX();
      double y = titan.getY();
      double z = titan.getZ();
      float yaw = titan.getYaw();
      titan.kill();
      VillagerEntity villager = MCACompat.createVillager(level);
      if (villager != null) {
         villager.refreshPositionAndAngles(x, y, z, yaw, 0.0F);
         villager.initialize(level, level.getLocalDifficulty(villager.getBlockPos()), SpawnReason.CONVERSION, null, null);
         villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.NONE));
         level.spawnEntity(villager);
         TitanPowerData data = TitanPowerData.get(level);
         data.setPower(villager.getUuid(), power);
         broadcastPowerAdd(level, villager.getId(), power);
      }
   }

   public static void broadcastPowerAdd(ServerWorld level, int entityId, TitanPowerType power) {
      PoweredVillagerSyncPayload payload = PoweredVillagerSyncPayload.addPower(entityId, power.ordinal());

      for (ServerPlayerEntity player : level.getPlayers()) {
         ServerPlayNetworking.send(player, payload);
      }
   }

   public static void broadcastPowerRemoval(ServerWorld level, int entityId) {
      PoweredVillagerSyncPayload payload = PoweredVillagerSyncPayload.removePower(entityId);

      for (ServerPlayerEntity player : level.getPlayers()) {
         ServerPlayNetworking.send(player, payload);
      }
   }

   public static void syncAllPoweredVillagersToPlayer(ServerPlayerEntity player, ServerWorld level) {
      TitanPowerData data = TitanPowerData.get(level);
      Map<Integer, Integer> entityIdToPower = new HashMap<>();

      for (Entry<UUID, TitanPowerType> entry : data.getPowerMap().entrySet()) {
         Entity entity = level.getEntity(entry.getKey());
         if (entity instanceof VillagerEntity) {
            entityIdToPower.put(entity.getId(), entry.getValue().ordinal());
         }
      }

      if (!entityIdToPower.isEmpty()) {
         PoweredVillagerSyncPayload payload = PoweredVillagerSyncPayload.fullSync(entityIdToPower);
         ServerPlayNetworking.send(player, payload);
      }
   }

   public static void broadcastShifterClaimed(ServerWorld level, TitanPowerType power) {
      if (DannysAot.isShifterSnitchingEnabled(level)) {
         int color = getShifterColor(power);
         Text message = Text.literal(power.getDisplayName())
            .styled(style -> style.withColor(color))
            .append(Text.literal(" has been claimed").styled(style -> style.withColor(11184810)));

         for (ServerPlayerEntity p : level.getServer().getPlayerManager().getPlayerList()) {
            p.sendMessage(message, false);
         }
      }
   }

   public static void broadcastShifterUnclaimed(ServerWorld level, TitanPowerType power) {
      if (DannysAot.isShifterSnitchingEnabled(level)) {
         int color = getShifterColor(power);
         Text message = Text.literal(power.getDisplayName())
            .styled(style -> style.withColor(color))
            .append(Text.literal(" is now unclaimed").styled(style -> style.withColor(11184810)));

         for (ServerPlayerEntity p : level.getServer().getPlayerManager().getPlayerList()) {
            p.sendMessage(message, false);
         }
      }
   }

   public static int getShifterColor(TitanPowerType power) {
      return switch (power) {
         case ATTACK -> 5635925;
         case COLOSSAL -> 16733525;
         case ARMORED -> 16753920;
         case BEAST -> 9132327;
         case FEMALE -> 16738740;
         case WARHAMMER -> 8388736;
         case JAW -> 16753920;
      };
   }

   private record PathsReturnData(RegistryKey<World> returnDim, double x, double y, double z, long returnAtTick) {
   }
}
