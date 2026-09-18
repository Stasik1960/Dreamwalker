package daot;

import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class YmirCurseHandler {
   private static final long TICKS_PER_DAY = 24000L;
   private static final int CHECK_INTERVAL_TICKS = 20;
   private static final Set<String> SHIFTER_TAGS = Set.of("attack", "colossal", "armored", "beast", "female", "warhammer");
   private static long tickCounter = 0L;

   private static long curseDurationTicks() {
      return Math.max(1L, (long)ModConfig.get().ymirCurseDurationDays) * 24000L;
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(YmirCurseHandler::onTick);
      ServerLivingEntityEvents.AFTER_DEATH.register(YmirCurseHandler::onDeath);
   }

   private static void onTick(MinecraftServer server) {
      tickCounter++;
      if (tickCounter % 20L == 0L) {
         ServerWorld overworld = server.getOverworld();
         if (DannysAot.isYmirCurseEnabled(overworld)) {
            YmirCurseData data = YmirCurseData.get(server);
            long gameTime = overworld.getTime();
            long durationTicks = curseDurationTicks();

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
               boolean hasTag = hasAnyShifterTag(player);
               long startTick = data.getStartTick(player.getUuid());
               if (hasTag) {
                  if (startTick < 0L) {
                     data.setStartTick(player.getUuid(), gameTime);
                     applyCurseEffect(player, (int)Math.min(durationTicks, 2147483647L));
                  } else {
                     long elapsed = gameTime - startTick;
                     if (elapsed < durationTicks) {
                        int remaining = (int)Math.min(durationTicks - elapsed, 2147483647L);
                        refreshCurseEffect(player, remaining);
                     } else {
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 0, false, true, true));
                     }
                  }
               } else if (startTick >= 0L) {
                  data.clear(player.getUuid());
                  player.removeStatusEffect(DannysAot.CURSE_OF_YMIR_EFFECT);
               }
            }
         }
      }
   }

   private static void onDeath(LivingEntity entity, DamageSource source) {
      if (entity instanceof ServerPlayerEntity player) {
         ServerWorld level = player.getServerWorld();
         if (DannysAot.isYmirCurseEnabled(level)) {
            MinecraftServer server = level.getServer();
            if (server != null) {
               YmirCurseData data = YmirCurseData.get(server);
               long startTick = data.getStartTick(player.getUuid());
               if (startTick >= 0L) {
                  long elapsed = level.getTime() - startTick;
                  if (elapsed >= curseDurationTicks()) {
                     for (String tag : SHIFTER_TAGS) {
                        player.removeScoreboardTag(tag);
                     }

                     data.clear(player.getUuid());
                  }
               }
            }
         }
      }
   }

   private static void applyCurseEffect(ServerPlayerEntity player, int duration) {
      player.addStatusEffect(new StatusEffectInstance(DannysAot.CURSE_OF_YMIR_EFFECT, duration, 0, false, false, false));
   }

   private static void refreshCurseEffect(ServerPlayerEntity player, int remaining) {
      StatusEffectInstance existing = player.getStatusEffect(DannysAot.CURSE_OF_YMIR_EFFECT);
      if (existing == null || Math.abs(existing.getDuration() - remaining) > 40) {
         applyCurseEffect(player, remaining);
      }
   }

   private static boolean hasAnyShifterTag(ServerPlayerEntity player) {
      for (String tag : SHIFTER_TAGS) {
         if (player.getCommandTags().contains(tag)) {
            return true;
         }
      }

      return false;
   }
}
