package daot;

import daot.network.ShifterMarkSyncPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class ShifterMarkTracker {
   public static final int ACTIVE_AFTER_DISMOUNT_TICKS = 1200;
   public static final int FADE_DURATION_TICKS = 600;
   private static final Map<UUID, Long> pendingWineClears = new HashMap<>();
   private static final Map<UUID, ShifterMarkTracker.MarkState> serverStates = new HashMap<>();
   private static final Map<UUID, ShifterMarkTracker.MarkState> clientStates = new ConcurrentHashMap<>();

   public static String getMarkType(Entity entity) {
      if (entity instanceof TestShifterTitanEntity) {
         return "jaw";
      } else if (entity instanceof AttackTitanEntity) {
         return "attack";
      } else if (entity instanceof ArmoredTitanEntity) {
         return "armored";
      } else if (entity instanceof BeastTitanEntity) {
         return "beast";
      } else if (entity instanceof ColossalTitanEntity) {
         return "colossal";
      } else if (entity instanceof FemaleTitanEntity) {
         return "female";
      } else {
         return entity instanceof WarhammerTitanEntity ? "warhammer" : null;
      }
   }

   public static void markEntered(ServerPlayerEntity player, Entity shifter) {
      String type = getMarkType(shifter);
      if (type != null) {
         ShifterMarkTracker.MarkState existing = serverStates.get(player.getUuid());
         if (existing == null || !type.equals(existing.markType()) || existing.dismountedAtGameTime() != -1L) {
            ShifterMarkTracker.MarkState state = new ShifterMarkTracker.MarkState(type, -1L, -1L);
            serverStates.put(player.getUuid(), state);
            broadcast(player.getServerWorld(), player.getUuid(), state);
         }
      }
   }

   public static void markFullyDismounted(ServerPlayerEntity player, Entity shifter) {
      String type = getMarkType(shifter);
      if (type != null) {
         long now = player.getServerWorld().getTime();
         ShifterMarkTracker.MarkState existing = serverStates.get(player.getUuid());
         String resolvedType = existing != null && existing.markType() != null ? existing.markType() : type;
         ShifterMarkTracker.MarkState state = new ShifterMarkTracker.MarkState(resolvedType, now, -1L);
         serverStates.put(player.getUuid(), state);
         broadcast(player.getServerWorld(), player.getUuid(), state);
      }
   }

   public static void syncToPlayer(ServerPlayerEntity joiningPlayer) {
      for (Entry<UUID, ShifterMarkTracker.MarkState> entry : serverStates.entrySet()) {
         ShifterMarkTracker.MarkState s = entry.getValue();
         ServerPlayNetworking.send(joiningPlayer, new ShifterMarkSyncPayload(entry.getKey(), s.markType(), s.dismountedAtGameTime(), s.fadeStartedAtGameTime()));
      }
   }

   public static void registerServerTick() {
      ServerTickEvents.END_SERVER_TICK.register(ShifterMarkTracker::tickServer);
   }

   private static void tickServer(MinecraftServer server) {
      processPendingClears(server);
      if (!serverStates.isEmpty()) {
         List<UUID> finished = new ArrayList<>();

         for (Entry<UUID, ShifterMarkTracker.MarkState> entry : serverStates.entrySet()) {
            UUID uuid = entry.getKey();
            ShifterMarkTracker.MarkState state = entry.getValue();
            if (state.dismountedAtGameTime() != -1L) {
               ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
               ServerWorld level = player != null ? player.getServerWorld() : server.getOverworld();
               if (level != null) {
                  long now = level.getTime();
                  long elapsed = now - state.dismountedAtGameTime();
                  boolean stealthOn = player != null && player.getCommandTags().contains("titan_stealth");
                  long newFadeStart = state.fadeStartedAtGameTime();
                  if (elapsed < 1200L) {
                     if (newFadeStart != -1L) {
                        newFadeStart = -1L;
                     }
                  } else if (stealthOn) {
                     if (newFadeStart != -1L) {
                        newFadeStart = -1L;
                     }
                  } else if (newFadeStart == -1L) {
                     newFadeStart = now;
                  }

                  if (newFadeStart != -1L && now - newFadeStart >= 600L) {
                     finished.add(uuid);
                  } else if (newFadeStart != state.fadeStartedAtGameTime()) {
                     ShifterMarkTracker.MarkState updated = new ShifterMarkTracker.MarkState(state.markType(), state.dismountedAtGameTime(), newFadeStart);
                     entry.setValue(updated);
                     broadcast(level, uuid, updated);
                  }
               }
            }
         }

         for (UUID uuid : finished) {
            serverStates.remove(uuid);
            broadcastClear(server, uuid);
         }
      }
   }

   private static void broadcast(ServerWorld level, UUID playerUuid, ShifterMarkTracker.MarkState state) {
      ShifterMarkSyncPayload payload = new ShifterMarkSyncPayload(playerUuid, state.markType(), state.dismountedAtGameTime(), state.fadeStartedAtGameTime());

      for (ServerPlayerEntity other : PlayerLookup.world(level)) {
         ServerPlayNetworking.send(other, payload);
      }
   }

   private static void broadcastClear(MinecraftServer server, UUID playerUuid) {
      ShifterMarkSyncPayload payload = new ShifterMarkSyncPayload(playerUuid, "", -1L, -1L);

      for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(other, payload);
      }
   }

   public static void clearMarksAfterDelay(ServerPlayerEntity player, int delayTicks) {
      pendingWineClears.put(player.getUuid(), player.getWorld().getTime() + delayTicks);
   }

   public static void processPendingClears(MinecraftServer server) {
      if (!pendingWineClears.isEmpty()) {
         long now = server.getOverworld().getTime();
         Iterator<Entry<UUID, Long>> it = pendingWineClears.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Long> entry = it.next();
            if (now >= entry.getValue()) {
               UUID uuid = entry.getKey();
               if (serverStates.remove(uuid) != null) {
                  broadcastClear(server, uuid);
               }

               it.remove();
            }
         }
      }
   }

   public static void onPlayerLeave(UUID playerUuid, MinecraftServer server) {
      if (serverStates.remove(playerUuid) != null) {
         broadcastClear(server, playerUuid);
      }

      pendingWineClears.remove(playerUuid);
   }

   public static ShifterMarkTracker.MarkState getClientState(UUID playerUuid) {
      return clientStates.get(playerUuid);
   }

   public static void setClientState(UUID playerUuid, String markType, long dismountedAtGameTime, long fadeStartedAtGameTime) {
      if (markType != null && !markType.isEmpty()) {
         clientStates.put(playerUuid, new ShifterMarkTracker.MarkState(markType, dismountedAtGameTime, fadeStartedAtGameTime));
      } else {
         clientStates.remove(playerUuid);
      }
   }

   public static void clearClientStates() {
      clientStates.clear();
   }

   public record MarkState(String markType, long dismountedAtGameTime, long fadeStartedAtGameTime) {
   }
}
