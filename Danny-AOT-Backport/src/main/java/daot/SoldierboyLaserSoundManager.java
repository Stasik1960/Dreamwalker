package daot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.sound.SoundCategory;

@Environment(EnvType.CLIENT)
public final class SoldierboyLaserSoundManager {
   private static final float LASER_SOUND_VOLUME = 1.6F;
   private static final float LASER_SOUND_PITCH = 1.0F;
   private static final double LASER_SOUND_RADIUS = 150.0;
   private static final float FADE_IN = 0.5F;
   private static final float FADE_OUT = 0.5F;
   private static final int LOOP_DURATION_TICKS = 100;
   private static final int CROSSFADE_TICKS = 5;
   private static final Set<UUID> KNOWN = new HashSet<>();
   private static final Map<UUID, List<FadingLoopSound>> SOUNDS = new HashMap<>();

   private SoldierboyLaserSoundManager() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(SoldierboyLaserSoundManager::tick);
   }

   public static void clearAll() {
      for (List<FadingLoopSound> list : SOUNDS.values()) {
         for (FadingLoopSound s : list) {
            if (s != null) {
               s.fadeOut();
            }
         }
      }

      SOUNDS.clear();
      KNOWN.clear();
   }

   private static void tick(MinecraftClient mc) {
      if (mc.world == null) {
         clearAll();
      } else {
         Set<UUID> current = new HashSet<>(SoldierboyClientHandler.laserFirers());

         for (UUID id : current) {
            if (!KNOWN.contains(id)) {
               startSound(id);
            }
         }

         for (UUID idx : KNOWN) {
            if (!current.contains(idx)) {
               stopSound(idx);
            }
         }

         KNOWN.clear();
         KNOWN.addAll(current);
      }
   }

   private static void startSound(UUID firerId) {
      if (!SOUNDS.containsKey(firerId)) {
         SOUNDS.put(firerId, new ArrayList<>());
         spawnLoopInstance(firerId);
      } else {
         for (FadingLoopSound s : SOUNDS.get(firerId)) {
            s.cancelFadeOut();
         }
      }
   }

   private static void spawnLoopInstance(UUID firerId) {
      if (SOUNDS.containsKey(firerId)) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null) {
            if (mc.world.getPlayerByUuid(firerId) instanceof AbstractClientPlayerEntity acp) {
               Runnable spawnNext = () -> spawnLoopInstance(firerId);
               FadingLoopSound sound = new FadingLoopSound(ModSounds.LASER, SoundCategory.PLAYERS, 1.6F, 1.0F, acp, 0.5F, 0.5F, 150.0, 100, 5, spawnNext);
               List<FadingLoopSound> list = SOUNDS.get(firerId);
               list.removeIf(s -> !mc.getSoundManager().isPlaying(s));
               list.add(sound);
               mc.getSoundManager().playNextTick(sound);
            }
         }
      }
   }

   private static void stopSound(UUID firerId) {
      List<FadingLoopSound> list = SOUNDS.remove(firerId);
      if (list != null) {
         for (FadingLoopSound s : list) {
            s.fadeOut();
         }
      }
   }

   public static void forceStopLocal(UUID localUuid) {
      stopSound(localUuid);
   }
}
