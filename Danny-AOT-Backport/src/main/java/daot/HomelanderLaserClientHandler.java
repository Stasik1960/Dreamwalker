package daot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class HomelanderLaserClientHandler {
   private static final Set<UUID> ACTIVE_FIRERS = ConcurrentHashMap.newKeySet();
   private static final Map<UUID, FadingLoopSound> SOUNDS = new HashMap<>();
   private static final Map<UUID, Long> EXTEND_START_TICKS = new ConcurrentHashMap<>();
   public static final int EXTEND_DURATION_TICKS = 3;
   private static final Map<UUID, Vec3d> DIRECTIONS = new ConcurrentHashMap<>();
   private static final double LASER_SOUND_RADIUS = 64.0;
   private static final float LASER_SOUND_VOLUME = 1.6F;
   private static final float LASER_SOUND_PITCH = 2.0F;
   private static final float LASER_FADE_IN = 0.4F;
   private static final float LASER_FADE_OUT = 0.4F;

   private HomelanderLaserClientHandler() {
   }

   public static Set<UUID> activeFirers() {
      return Collections.unmodifiableSet(ACTIVE_FIRERS);
   }

   public static void setActive(UUID uuid, boolean active) {
      if (active) {
         if (ACTIVE_FIRERS.add(uuid)) {
            startSound(uuid);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null) {
               EXTEND_START_TICKS.put(uuid, mc.world.getTime());
            }
         }
      } else {
         if (ACTIVE_FIRERS.remove(uuid)) {
            stopSound(uuid);
         }

         DIRECTIONS.remove(uuid);
         EXTEND_START_TICKS.remove(uuid);
      }
   }

   public static float getExtendProgress(UUID uuid, float partialTick) {
      Long start = EXTEND_START_TICKS.get(uuid);
      if (start == null) {
         return 1.0F;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world == null) {
            return 1.0F;
         } else {
            double elapsed = (float)mc.world.getTime() + partialTick - (float)start.longValue();
            return elapsed >= 3.0 ? 1.0F : (float)Math.max(0.0, elapsed / 3.0);
         }
      }
   }

   public static void forceStopLocal(UUID localUuid) {
      if (ACTIVE_FIRERS.remove(localUuid)) {
         stopSound(localUuid);
      }

      DIRECTIONS.remove(localUuid);
      EXTEND_START_TICKS.remove(localUuid);
   }

   public static void setDirection(UUID firerId, Vec3d dir) {
      DIRECTIONS.put(firerId, dir);
   }

   public static Vec3d getDirection(UUID firerId) {
      return DIRECTIONS.get(firerId);
   }

   public static void clearAll() {
      for (FadingLoopSound s : SOUNDS.values()) {
         if (s != null) {
            s.fadeOut();
         }
      }

      SOUNDS.clear();
      ACTIVE_FIRERS.clear();
      DIRECTIONS.clear();
      EXTEND_START_TICKS.clear();
   }

   private static void startSound(UUID firerId) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null) {
         if (mc.world.getPlayerByUuid(firerId) instanceof AbstractClientPlayerEntity acp) {
            FadingLoopSound existing = SOUNDS.get(firerId);
            if (existing != null) {
               existing.cancelFadeOut();
            } else {
               FadingLoopSound sound = new FadingLoopSound(SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.PLAYERS, 1.6F, 2.0F, acp, 0.4F, 0.4F, 64.0);
               SOUNDS.put(firerId, sound);
               mc.getSoundManager().play(sound);
            }
         }
      }
   }

   private static void stopSound(UUID firerId) {
      FadingLoopSound sound = SOUNDS.remove(firerId);
      if (sound != null) {
         sound.fadeOut();
      }
   }
}
