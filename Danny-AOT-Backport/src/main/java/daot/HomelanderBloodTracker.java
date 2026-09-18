package daot;

import daot.network.HomelanderBloodSyncPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class HomelanderBloodTracker {
   public static final int HOLD_TICKS_LOW = 600;
   public static final int HOLD_TICKS_STAGE4 = 1200;
   public static final int FADE_DURATION_TICKS = 600;
   public static final int SONIC_BOOM_FADE_TICKS = 60;
   public static final int MAX_M1_STAGE = 3;
   public static final int FLY_STAGE = 4;
   private static final Map<UUID, HomelanderBloodTracker.BloodState> serverStates = new HashMap<>();
   private static final Map<UUID, HomelanderBloodTracker.BloodState> clientStates = new ConcurrentHashMap<>();
   public static final int ATRAIN_FADE_DURATION_TICKS = 200;

   public static void onM1Hit(ServerPlayerEntity player) {
      long now = player.getServerWorld().getTime();
      HomelanderBloodTracker.BloodState existing = serverStates.get(player.getUuid());
      int newStage;
      if (existing == null) {
         newStage = 1;
      } else if (existing.stage() == 4) {
         newStage = 4;
      } else {
         newStage = Math.min(existing.stage() + 1, 3);
      }

      HomelanderBloodTracker.BloodState state = new HomelanderBloodTracker.BloodState(newStage, now, -1L, 600);
      serverStates.put(player.getUuid(), state);
      broadcast(player.getServerWorld(), player.getUuid(), state);
   }

   public static void onFlyHit(ServerPlayerEntity player) {
      long now = player.getServerWorld().getTime();
      HomelanderBloodTracker.BloodState state = new HomelanderBloodTracker.BloodState(4, now, -1L, 600);
      serverStates.put(player.getUuid(), state);
      broadcast(player.getServerWorld(), player.getUuid(), state);
   }

   public static void onAtrainHit(ServerPlayerEntity player) {
      long now = player.getServerWorld().getTime();
      HomelanderBloodTracker.BloodState state = new HomelanderBloodTracker.BloodState(4, now, now, 200);
      serverStates.put(player.getUuid(), state);
      broadcast(player.getServerWorld(), player.getUuid(), state);
   }

   public static void onSonicBoom(ServerPlayerEntity player) {
      HomelanderBloodTracker.BloodState existing = serverStates.get(player.getUuid());
      if (existing != null && existing.stage() == 4) {
         long now = player.getServerWorld().getTime();
         float currentAlpha;
         if (existing.fadeStartedAtGameTime() == -1L) {
            currentAlpha = 1.0F;
         } else {
            long fadeElapsed = now - existing.fadeStartedAtGameTime();
            int dur = Math.max(1, existing.fadeDurationTicks());
            if (fadeElapsed <= 0L) {
               currentAlpha = 1.0F;
            } else if (fadeElapsed >= dur) {
               currentAlpha = 0.0F;
            } else {
               currentAlpha = 1.0F - (float)fadeElapsed / dur;
            }
         }

         if (!(currentAlpha <= 0.0F)) {
            long backdate = (long)((1.0F - currentAlpha) * 60.0F);
            long newFadeStart = now - backdate;
            HomelanderBloodTracker.BloodState state = new HomelanderBloodTracker.BloodState(4, existing.startedAtGameTime(), newFadeStart, 60);
            serverStates.put(player.getUuid(), state);
            broadcast(player.getServerWorld(), player.getUuid(), state);
         }
      }
   }

   public static void syncToPlayer(ServerPlayerEntity joiningPlayer) {
      for (Entry<UUID, HomelanderBloodTracker.BloodState> entry : serverStates.entrySet()) {
         HomelanderBloodTracker.BloodState s = entry.getValue();
         ServerPlayNetworking.send(
            joiningPlayer, new HomelanderBloodSyncPayload(entry.getKey(), s.stage(), s.startedAtGameTime(), s.fadeStartedAtGameTime(), s.fadeDurationTicks())
         );
      }
   }

   public static void registerServerTick() {
      ServerTickEvents.END_SERVER_TICK.register(HomelanderBloodTracker::tickServer);
   }

   private static void tickServer(MinecraftServer server) {
      if (!serverStates.isEmpty()) {
         List<UUID> finished = new ArrayList<>();

         for (Entry<UUID, HomelanderBloodTracker.BloodState> entry : serverStates.entrySet()) {
            UUID uuid = entry.getKey();
            HomelanderBloodTracker.BloodState state = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) {
               finished.add(uuid);
            } else {
               ServerWorld level = player.getServerWorld();
               long now = level.getTime();
               if (state.fadeStartedAtGameTime() != -1L) {
                  long fadeElapsed = now - state.fadeStartedAtGameTime();
                  if (fadeElapsed < 0L || fadeElapsed >= state.fadeDurationTicks()) {
                     finished.add(uuid);
                  }
               } else {
                  int holdTicks = state.stage() == 4 ? 1200 : 600;
                  long elapsed = now - state.startedAtGameTime();
                  if (elapsed < 0L) {
                     HomelanderBloodTracker.BloodState reset = new HomelanderBloodTracker.BloodState(state.stage(), now, -1L, 600);
                     entry.setValue(reset);
                     broadcast(level, uuid, reset);
                  } else if (elapsed >= holdTicks) {
                     if (state.stage() > 1 && state.stage() != 4) {
                        HomelanderBloodTracker.BloodState next = new HomelanderBloodTracker.BloodState(state.stage() - 1, now, -1L, 600);
                        entry.setValue(next);
                        broadcast(level, uuid, next);
                     } else {
                        HomelanderBloodTracker.BloodState fading = new HomelanderBloodTracker.BloodState(state.stage(), state.startedAtGameTime(), now, 600);
                        entry.setValue(fading);
                        broadcast(level, uuid, fading);
                     }
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

   private static void broadcast(ServerWorld level, UUID playerUuid, HomelanderBloodTracker.BloodState state) {
      HomelanderBloodSyncPayload payload = new HomelanderBloodSyncPayload(
         playerUuid, state.stage(), state.startedAtGameTime(), state.fadeStartedAtGameTime(), state.fadeDurationTicks()
      );

      for (ServerPlayerEntity other : PlayerLookup.world(level)) {
         ServerPlayNetworking.send(other, payload);
      }
   }

   private static void broadcastClear(MinecraftServer server, UUID playerUuid) {
      HomelanderBloodSyncPayload payload = new HomelanderBloodSyncPayload(playerUuid, 0, -1L, -1L, 600);

      for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(other, payload);
      }
   }

   public static void onPlayerLeave(UUID playerUuid, MinecraftServer server) {
      if (serverStates.remove(playerUuid) != null) {
         broadcastClear(server, playerUuid);
      }
   }

   public static HomelanderBloodTracker.BloodState getClientState(UUID playerUuid) {
      return clientStates.get(playerUuid);
   }

   public static void setClientState(UUID playerUuid, int stage, long startedAtGameTime, long fadeStartedAtGameTime, int fadeDurationTicks) {
      if (stage <= 0) {
         clientStates.remove(playerUuid);
      } else {
         clientStates.put(playerUuid, new HomelanderBloodTracker.BloodState(stage, startedAtGameTime, fadeStartedAtGameTime, fadeDurationTicks));
      }
   }

   public static void clearClientStates() {
      clientStates.clear();
   }

   public record BloodState(int stage, long startedAtGameTime, long fadeStartedAtGameTime, int fadeDurationTicks) {
   }
}
