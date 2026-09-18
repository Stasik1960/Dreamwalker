package daot;

import daot.network.HandcuffsSyncPayload;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

public class HandcuffsTracker {
   public static final String CUFFED_TAG = "handcuffed";
   private static final Map<UUID, HandcuffsTracker.CuffData> cuffedEntities = new ConcurrentHashMap<>();
   private static final Map<UUID, HandcuffsTracker.CuffingSession> activeSessions = new ConcurrentHashMap<>();
   private static final Map<UUID, UUID> escortedEntities = new ConcurrentHashMap<>();
   private static final Map<UUID, HandcuffsTracker.CuffedClientData> clientCuffedStates = new ConcurrentHashMap<>();
   public static final int CUFF_DURATION_TICKS = 50;
   private static final Map<UUID, Long> escortCooldowns = new ConcurrentHashMap<>();

   public static boolean isCuffed(UUID entityUuid) {
      return cuffedEntities.containsKey(entityUuid);
   }

   public static boolean isCuffed(Entity entity) {
      return cuffedEntities.containsKey(entity.getUuid()) || entity.getCommandTags().contains("handcuffed");
   }

   public static void onInteractEntity(ServerPlayerEntity cuffer, Entity target) {
      UUID cufferUUID = cuffer.getUuid();
      HandcuffsTracker.CuffingSession existing = activeSessions.get(cufferUUID);
      if (existing == null || existing.targetEntityId != target.getId()) {
         String name = target instanceof PlayerEntity p ? p.getDisplayName().getString() : "Villager";
         activeSessions.put(cufferUUID, new HandcuffsTracker.CuffingSession(target.getId(), target.getUuid(), name));
      }
   }

   public static boolean hasActiveSession(UUID cufferUUID) {
      return activeSessions.containsKey(cufferUUID);
   }

   public static HandcuffsTracker.CuffingSession getSession(UUID cufferUUID) {
      return activeSessions.get(cufferUUID);
   }

   public static void cancelCuffingSession(UUID cufferUuid) {
      activeSessions.remove(cufferUuid);
   }

   public static void startEscorting(UUID cuffedEntityUuid, UUID escorterUuid) {
      escortedEntities.put(cuffedEntityUuid, escorterUuid);
   }

   public static void stopEscorting(UUID cuffedEntityUuid) {
      escortedEntities.remove(cuffedEntityUuid);
   }

   public static boolean isBeingEscorted(UUID cuffedEntityUuid) {
      return escortedEntities.containsKey(cuffedEntityUuid);
   }

   public static UUID getEscorter(UUID cuffedEntityUuid) {
      return escortedEntities.get(cuffedEntityUuid);
   }

   public static boolean isEscortCooldownActive(UUID playerUuid) {
      Long lastTick = escortCooldowns.get(playerUuid);
      return lastTick != null && System.currentTimeMillis() - lastTick < 200L;
   }

   public static void setEscortCooldown(UUID playerUuid) {
      escortCooldowns.put(playerUuid, System.currentTimeMillis());
   }

   public static void forceRemoveCuffed(UUID entityUuid) {
      cuffedEntities.remove(entityUuid);
   }

   public static void cuffEntity(Entity target, ServerPlayerEntity cuffer) {
      UUID targetUUID = target.getUuid();
      cuffedEntities.put(targetUUID, new HandcuffsTracker.CuffData(cuffer.getUuid()));
      if (target instanceof ServerPlayerEntity cuffedPlayer) {
         cuffedPlayer.addCommandTag("handcuffed");
         cuffedPlayer.sendMessage(Text.literal("You have been cuffed! Leaving will have consequences!").formatted(Formatting.RED));
         broadcastCuffState(cuffedPlayer, true);
      }

      if (target instanceof VillagerEntity villager) {
         villager.addCommandTag("handcuffed");
         villager.getNavigation().stop();
         villager.setAiDisabled(true);
      }

      target.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.HANDCUFF_LOCK, SoundCategory.PLAYERS, 1.0F, 1.0F);
   }

   public static void uncuffEntity(Entity target, PlayerEntity cuffsReceiver) {
      UUID targetUUID = target.getUuid();
      cuffedEntities.remove(targetUUID);
      escortedEntities.remove(targetUUID);
      if (target.hasVehicle()) {
         Entity vehicle = target.getVehicle();
         if (target instanceof ServerPlayerEntity prisoner) {
            prisoner.addCommandTag("dannysaot_allow_dismount");
         }

         target.stopRiding();
         broadcastPassengerSync(vehicle);
         if (target instanceof ServerPlayerEntity prisoner) {
            prisoner.networkHandler.requestTeleport(vehicle.getX(), vehicle.getY(), vehicle.getZ(), prisoner.getYaw(), prisoner.getPitch());
         }
      }

      if (target instanceof ServerPlayerEntity cuffedPlayer) {
         cuffedPlayer.removeScoreboardTag("handcuffed");
         cuffedPlayer.sendMessage(Text.literal("You have been freed from the cuffs!").formatted(Formatting.GREEN), true);
         broadcastCuffState(cuffedPlayer, false);
      }

      if (target instanceof VillagerEntity villager) {
         villager.removeScoreboardTag("handcuffed");
         villager.setAiDisabled(false);
      }

      target.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.HANDCUFF_UNLOCK, SoundCategory.PLAYERS, 1.0F, 1.0F);
      if (cuffsReceiver != null && !target.getWorld().isClient()) {
         ItemStack cuffsStack = new ItemStack(DannysAot.HANDCUFFS);
         if (!cuffsReceiver.getInventory().insertStack(cuffsStack)) {
            cuffsReceiver.dropItem(cuffsStack, false);
         }
      }
   }

   public static void serverTick(MinecraftServer server) {
      for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
         if (onlinePlayer.getCommandTags().contains("handcuffed") && !cuffedEntities.containsKey(onlinePlayer.getUuid())) {
            cuffedEntities.put(onlinePlayer.getUuid(), new HandcuffsTracker.CuffData(null));
            broadcastCuffState(onlinePlayer, true);
         }
      }

      for (Entry<UUID, HandcuffsTracker.CuffData> entry : cuffedEntities.entrySet()) {
         UUID uuid = entry.getKey();
         HandcuffsTracker.CuffData data = entry.getValue();
         ServerPlayerEntity cuffedPlayer = server.getPlayerManager().getPlayer(uuid);
         if (cuffedPlayer != null) {
            Vec3d vel = cuffedPlayer.getVelocity();
            cuffedPlayer.setVelocity(0.0, Math.min(vel.y, 0.0), 0.0);
            cuffedPlayer.forwardSpeed = 0.0F;
            cuffedPlayer.sidewaysSpeed = 0.0F;
            boolean isCrouching = cuffedPlayer.isSneaking();
            if (isCrouching) {
               data.breakFreeTicks++;
               float secondsRemaining = 30.0F - data.breakFreeTicks / 20.0F;
               if (secondsRemaining <= 0.0F) {
                  uncuffEntity(cuffedPlayer, cuffedPlayer);
                  continue;
               }

               String msg = String.format("Breaking Cuffs (%.1f)", secondsRemaining);
               cuffedPlayer.sendMessage(Text.literal(msg).formatted(Formatting.YELLOW), true);
               if (data.breakFreeTicks % 10 == 0) {
                  cuffedPlayer.getWorld()
                     .playSound(
                        null,
                        cuffedPlayer.getX(),
                        cuffedPlayer.getY(),
                        cuffedPlayer.getZ(),
                        ModSounds.HANDCUFF_LOCK,
                        SoundCategory.PLAYERS,
                        0.5F,
                        1.3F + (float)(Math.random() * 0.2)
                     );
               }

               if (data.breakFreeTicks % 10 == 0 && cuffedPlayer.getWorld() instanceof ServerWorld serverLevel) {
                  serverLevel.spawnParticles(
                     new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.getDefaultState()),
                     cuffedPlayer.getX(),
                     cuffedPlayer.getY() + 0.8,
                     cuffedPlayer.getZ(),
                     8,
                     0.3,
                     0.2,
                     0.3,
                     0.05
                  );
               }
            } else if (data.breakFreeTicks > 0) {
               data.breakFreeTicks = 0;
            }

            data.wasCrouchingLastTick = isCrouching;
         } else {
            Entity entity = null;

            for (ServerWorld level : server.getWorlds()) {
               entity = level.getEntity(uuid);
               if (entity != null) {
                  break;
               }
            }
         }
      }

      Iterator<Entry<UUID, UUID>> escortIt = escortedEntities.entrySet().iterator();

      while (escortIt.hasNext()) {
         Entry<UUID, UUID> entryx = escortIt.next();
         UUID cuffedUuid = entryx.getKey();
         UUID escorterUuid = entryx.getValue();
         ServerPlayerEntity escorter = server.getPlayerManager().getPlayer(escorterUuid);
         if (escorter == null) {
            escortIt.remove();
         } else {
            Entity cuffedEntity = null;
            ServerPlayerEntity cuffedAsPlayer = server.getPlayerManager().getPlayer(cuffedUuid);
            if (cuffedAsPlayer != null) {
               cuffedEntity = cuffedAsPlayer;
            } else {
               for (ServerWorld levelx : server.getWorlds()) {
                  cuffedEntity = levelx.getEntity(cuffedUuid);
                  if (cuffedEntity != null) {
                     break;
                  }
               }
            }

            if (cuffedEntity != null && isCuffed(cuffedEntity)) {
               float yawRad = (float)Math.toRadians(escorter.getYaw());
               double behindX = escorter.getX() + Math.sin(yawRad) * 1.5;
               double behindZ = escorter.getZ() - Math.cos(yawRad) * 1.5;
               cuffedEntity.requestTeleport(behindX, escorter.getY(), behindZ);
               cuffedEntity.setVelocity(0.0, 0.0, 0.0);
               if (cuffedEntity instanceof ServerPlayerEntity sp) {
                  sp.networkHandler.requestTeleport(behindX, escorter.getY(), behindZ, escorter.getYaw(), 0.0F);
               }
            } else {
               escortIt.remove();
            }
         }
      }
   }

   public static void onPlayerDamaged(UUID playerUuid) {
      HandcuffsTracker.CuffData data = cuffedEntities.get(playerUuid);
      if (data != null) {
         data.breakFreeTicks = 0;
      }
   }

   public static void onPlayerJoin(ServerPlayerEntity player) {
      if (player.getCommandTags().contains("handcuffed") && !cuffedEntities.containsKey(player.getUuid())) {
         cuffedEntities.put(player.getUuid(), new HandcuffsTracker.CuffData(null));
      }

      if (cuffedEntities.containsKey(player.getUuid()) || player.getCommandTags().contains("handcuffed")) {
         boolean hasShifter = hasAnyShifterTag(player);
         ServerPlayNetworking.send(player, new HandcuffsSyncPayload(player.getUuid(), true, hasShifter));
         if (!cuffedEntities.containsKey(player.getUuid())) {
            cuffedEntities.put(player.getUuid(), new HandcuffsTracker.CuffData(null));
         }
      }

      for (UUID cuffedUuid : cuffedEntities.keySet()) {
         if (!cuffedUuid.equals(player.getUuid())) {
            ServerPlayerEntity cuffedPlayer = player.getServer().getPlayerManager().getPlayer(cuffedUuid);
            boolean hasShifter = cuffedPlayer != null && hasAnyShifterTag(cuffedPlayer);
            ServerPlayNetworking.send(player, new HandcuffsSyncPayload(cuffedUuid, true, hasShifter));
         }
      }
   }

   private static boolean hasAnyShifterTag(ServerPlayerEntity player) {
      return player.getCommandTags().contains("attack")
         || player.getCommandTags().contains("colossal")
         || player.getCommandTags().contains("armored")
         || player.getCommandTags().contains("beast")
         || player.getCommandTags().contains("female")
         || player.getCommandTags().contains("warhammer");
   }

   private static void broadcastCuffState(ServerPlayerEntity target, boolean cuffed) {
      boolean hasShifter = hasAnyShifterTag(target);
      HandcuffsSyncPayload payload = new HandcuffsSyncPayload(target.getUuid(), cuffed, hasShifter);

      for (ServerPlayerEntity player : PlayerLookup.world(target.getServerWorld())) {
         ServerPlayNetworking.send(player, payload);
      }
   }

   public static void broadcastPassengerSync(Entity vehicle) {
      if (vehicle.getWorld() instanceof ServerWorld serverLevel) {
         EntityPassengersSetS2CPacket var6 = new EntityPassengersSetS2CPacket(vehicle);

         for (ServerPlayerEntity tracking : PlayerLookup.tracking(vehicle)) {
            tracking.networkHandler.sendPacket(var6);
         }

         if (vehicle instanceof ServerPlayerEntity sp) {
            sp.networkHandler.sendPacket(var6);
         }

         for (Entity passenger : vehicle.getPassengerList()) {
            if (passenger instanceof ServerPlayerEntity sp) {
               sp.networkHandler.sendPacket(var6);
            }
         }
      }
   }

   public static boolean isClientCuffed(UUID playerUuid) {
      HandcuffsTracker.CuffedClientData data = clientCuffedStates.get(playerUuid);
      return data != null && data.cuffed;
   }

   public static boolean isClientCuffedShifter(UUID playerUuid) {
      HandcuffsTracker.CuffedClientData data = clientCuffedStates.get(playerUuid);
      return data != null && data.cuffed && data.hasShifter;
   }

   public static void setClientCuffedState(UUID playerUuid, boolean cuffed, boolean hasShifter) {
      if (cuffed) {
         clientCuffedStates.put(playerUuid, new HandcuffsTracker.CuffedClientData(true, hasShifter));
      } else {
         clientCuffedStates.remove(playerUuid);
      }
   }

   public static class CuffData {
      public final UUID cuffedBy;
      public int breakFreeTicks;
      public boolean wasCrouchingLastTick;

      public CuffData(UUID cuffedBy) {
         this.cuffedBy = cuffedBy;
         this.breakFreeTicks = 0;
         this.wasCrouchingLastTick = false;
      }
   }

   public static class CuffedClientData {
      public final boolean cuffed;
      public final boolean hasShifter;

      public CuffedClientData(boolean cuffed, boolean hasShifter) {
         this.cuffed = cuffed;
         this.hasShifter = hasShifter;
      }
   }

   public static class CuffingSession {
      public final int targetEntityId;
      public final UUID targetUUID;
      public final String targetName;
      public int ticksHeld;

      public CuffingSession(int targetEntityId, UUID targetUUID, String targetName) {
         this.targetEntityId = targetEntityId;
         this.targetUUID = targetUUID;
         this.targetName = targetName;
         this.ticksHeld = 0;
      }
   }
}
