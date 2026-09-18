package daot;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class DefeatedCarryTracker {
   public static final int DEFEAT_DURATION_TICKS = 900;
   private static final Map<UUID, Integer> defeated = new ConcurrentHashMap<>();

   private DefeatedCarryTracker() {
   }

   public static void markDefeated(UUID uuid) {
      defeated.put(uuid, 900);
   }

   public static boolean isDefeated(UUID uuid) {
      return defeated.containsKey(uuid);
   }

   public static void clear(UUID uuid) {
      defeated.remove(uuid);
   }

   public static boolean isIncapacitatedOrDefeated(PlayerEntity player) {
      return isDefeated(player.getUuid()) ? true : player.getVehicle() instanceof ShifterTitan shifter && shifter.isIncapacitated();
   }

   public static void tick(MinecraftServer server) {
      if (!defeated.isEmpty()) {
         for (UUID uuid : new ArrayList<>(defeated.keySet())) {
            int remaining = defeated.getOrDefault(uuid, 0) - 1;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (remaining > 0) {
               defeated.put(uuid, remaining);
               if (player != null) {
                  StatusEffectInstance weakness = player.getStatusEffect(StatusEffects.WEAKNESS);
                  if (weakness == null || weakness.getAmplifier() < 4) {
                     player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, remaining, 4, false, false, true));
                     player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, remaining, 2, false, false, true));
                     player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, remaining, 4, false, false, true));
                  }
               }
            } else {
               defeated.remove(uuid);
               if (player != null && player.getVehicle() instanceof PlayerEntity carrier) {
                  player.stopRiding();
                  HandcuffsTracker.broadcastPassengerSync(carrier);
               }
            }
         }
      }
   }
}
