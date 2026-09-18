package daot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundInstance.AttenuationType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

@Environment(EnvType.CLIENT)
public class ODMSoundManager {
   private static final Map<UUID, FadingLoopSound> gasBoostSounds = new HashMap<>();
   private static final Map<UUID, FadingLoopSound> flightSounds = new HashMap<>();
   private static final Map<Integer, FadingLoopSound> remoteGasBoostSounds = new HashMap<>();
   private static final Map<Integer, FadingLoopSound> remoteFlightSounds = new HashMap<>();
   private static final Map<Integer, ODMSoundManager.RemoteODMState> remotePrevStates = new HashMap<>();
   private static final float GAS_BOOST_FADE_IN_SPEED = 0.1F;
   private static final float GAS_BOOST_FADE_OUT_SPEED = 0.1F;
   private static final float FLIGHT_FADE_IN_SPEED = 0.1F;
   private static final float FLIGHT_FADE_OUT_SPEED = 0.05F;
   private static final double REMOTE_SOUND_DISTANCE = 80.0;

   public static void playLocalOneShot(SoundEvent sound, float volume, float pitch) {
      MinecraftClient.getInstance().getSoundManager().play(new ODMSoundManager.LocalOneShot(sound, volume, pitch));
   }

   public static void playGasBoostSound(ClientPlayerEntity player) {
      playGasBoostSound(player, 1.0F);
   }

   public static void playGasBoostSound(ClientPlayerEntity player, float pitch) {
      FadingLoopSound existingSound = gasBoostSounds.get(player.getUuid());
      if (existingSound != null) {
         if (!existingSound.isDone()) {
            if (!existingSound.isStopping()) {
               return;
            }

            existingSound.cancelFadeOut();
            return;
         }

         gasBoostSounds.remove(player.getUuid());
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      FadingLoopSound sound = new FadingLoopSound(ModSounds.GAS_BOOST, SoundCategory.PLAYERS, 1.0F, pitch, 0.0, 0.0, 0.0, 0.1F, 0.1F);
      mc.getSoundManager().play(sound);
      gasBoostSounds.put(player.getUuid(), sound);
   }

   public static void stopGasBoostSound(ClientPlayerEntity player) {
      FadingLoopSound sound = gasBoostSounds.get(player.getUuid());
      if (sound != null) {
         if (sound.isDone()) {
            gasBoostSounds.remove(player.getUuid());
         } else if (!sound.isStopping()) {
            sound.fadeOut();
         }
      }
   }

   public static void playFlightSound(ClientPlayerEntity player) {
      playFlightSound(player, 1.0F);
   }

   public static void playFlightSound(ClientPlayerEntity player, float pitch) {
      FadingLoopSound existingSound = flightSounds.get(player.getUuid());
      if (existingSound != null) {
         if (!existingSound.isDone()) {
            if (!existingSound.isStopping()) {
               return;
            }

            existingSound.cancelFadeOut();
            return;
         }

         flightSounds.remove(player.getUuid());
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      FadingLoopSound sound = new FadingLoopSound(ModSounds.ODM_FLIGHT, SoundCategory.PLAYERS, 0.6F, pitch, 0.0, 0.0, 0.0, 0.1F, 0.05F);
      mc.getSoundManager().play(sound);
      flightSounds.put(player.getUuid(), sound);
   }

   public static void stopFlightSound(ClientPlayerEntity player) {
      FadingLoopSound sound = flightSounds.get(player.getUuid());
      if (sound != null) {
         if (sound.isDone()) {
            flightSounds.remove(player.getUuid());
         } else if (!sound.isStopping()) {
            sound.fadeOut();
         }
      }
   }

   public static void stopAllSounds(ClientPlayerEntity player) {
      MinecraftClient mc = MinecraftClient.getInstance();
      FadingLoopSound gasSound = gasBoostSounds.remove(player.getUuid());
      if (gasSound != null && !gasSound.isDone()) {
         mc.getSoundManager().stop(gasSound);
      }

      FadingLoopSound flightSound = flightSounds.remove(player.getUuid());
      if (flightSound != null && !flightSound.isDone()) {
         mc.getSoundManager().stop(flightSound);
      }
   }

   public static void tickRemoteSounds() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null) {
         Map<Integer, RemoteHookTracker.RemoteHookData> allHooks = RemoteHookTracker.getAllHooks();
         int localPlayerId = mc.player.getId();
         Set<Integer> activeIds = new HashSet<>();

         for (Entry<Integer, RemoteHookTracker.RemoteHookData> entry : allHooks.entrySet()) {
            int entityId = entry.getKey();
            if (entityId != localPlayerId) {
               RemoteHookTracker.RemoteHookData data = entry.getValue();
               Entity entity = mc.world.getEntityById(entityId);
               if (entity != null) {
                  activeIds.add(entityId);
                  boolean hasLatchedHook = data.leftActive && !data.leftExtending && !data.leftRetracting
                     || data.rightActive && !data.rightExtending && !data.rightRetracting;
                  handleRemoteOneShots(entityId, entity, data);
                  float remotePitch = ODMTickHandler.getRemoteODMPitch(entity);
                  if (hasLatchedHook && !entity.isOnGround()) {
                     playRemoteLoopSound(remoteFlightSounds, entityId, entity, ModSounds.ODM_FLIGHT, 0.6F, remotePitch, 0.1F, 0.05F);
                  } else {
                     stopRemoteLoopSound(remoteFlightSounds, entityId);
                  }

                  if (data.isBoosting && hasLatchedHook) {
                     playRemoteLoopSound(remoteGasBoostSounds, entityId, entity, ModSounds.GAS_BOOST, 1.0F, remotePitch, 0.1F, 0.1F);
                  } else {
                     stopRemoteLoopSound(remoteGasBoostSounds, entityId);
                  }
               }
            }
         }

         cleanupRemoteSounds(remoteFlightSounds, activeIds);
         cleanupRemoteSounds(remoteGasBoostSounds, activeIds);
         remotePrevStates.keySet().retainAll(activeIds);
      }
   }

   private static void handleRemoteOneShots(int entityId, Entity entity, RemoteHookTracker.RemoteHookData data) {
      ODMSoundManager.RemoteODMState prev = remotePrevStates.get(entityId);
      if (prev == null) {
         prev = new ODMSoundManager.RemoteODMState();
         remotePrevStates.put(entityId, prev);
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      double dist = mc.player.getPos().distanceTo(entity.getPos());
      float remotePitch = ODMTickHandler.getRemoteODMPitch(entity);
      if (data.leftActive && data.leftExtending && !prev.leftActive) {
         playDistancedSound(entity, dist, ModSounds.TRIGGER, 0.1F, remotePitch);
      }

      if (data.rightActive && data.rightExtending && !prev.rightActive) {
         playDistancedSound(entity, dist, ModSounds.TRIGGER, 0.1F, remotePitch);
      }

      boolean remoteWearsApg = false;
      if (entity instanceof PlayerEntity p) {
         remoteWearsApg = DannysAot.isAPG(p.getEquippedStack(EquipmentSlot.LEGS).getItem());
      }

      if (data.leftActive && !data.leftExtending && !data.leftRetracting && prev.leftExtending) {
         SoundEvent hookSound = remoteWearsApg ? ModSounds.HOOK_SHOOT_APG : ModSounds.HOOK_SHOOT_1;
         float hookPitch = remoteWearsApg ? 0.95F : remotePitch;
         playDistancedSound(entity, dist, hookSound, remoteWearsApg ? 0.15F : 0.3F, hookPitch);
         playDistancedSound(entity, dist, ModSounds.HOOK_IMPACT, 0.4F, remotePitch);
      }

      if (data.rightActive && !data.rightExtending && !data.rightRetracting && prev.rightExtending) {
         SoundEvent hookSound = remoteWearsApg ? ModSounds.HOOK_SHOOT_APG : ModSounds.HOOK_SHOOT_2;
         float hookPitch = remoteWearsApg ? 0.95F : remotePitch;
         playDistancedSound(entity, dist, hookSound, remoteWearsApg ? 0.15F : 0.3F, hookPitch);
         playDistancedSound(entity, dist, ModSounds.HOOK_IMPACT, 0.4F, remotePitch);
      }

      if (data.leftRetracting && !prev.leftRetracting) {
         playDistancedSound(entity, dist, ModSounds.HOOK_RETRACT, 0.7F, remotePitch);
      }

      if (data.rightRetracting && !prev.rightRetracting) {
         playDistancedSound(entity, dist, ModSounds.HOOK_RETRACT, 0.7F, remotePitch);
      }

      prev.leftActive = data.leftActive;
      prev.leftExtending = data.leftExtending;
      prev.leftRetracting = data.leftRetracting;
      prev.rightActive = data.rightActive;
      prev.rightExtending = data.rightExtending;
      prev.rightRetracting = data.rightRetracting;
   }

   private static void playDistancedSound(Entity entity, double dist, SoundEvent sound, float baseVolume, float pitch) {
      if (!(dist >= 80.0)) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null) {
            float rangeVolume = baseVolume * 5.0F;
            mc.world.playSound(entity.getX(), entity.getY(), entity.getZ(), sound, SoundCategory.PLAYERS, rangeVolume, pitch, false);
         }
      }
   }

   private static void playRemoteLoopSound(
      Map<Integer, FadingLoopSound> soundMap, int entityId, Entity entity, SoundEvent sound, float volume, float pitch, float fadeIn, float fadeOut
   ) {
      FadingLoopSound existingSound = soundMap.get(entityId);
      if (existingSound != null) {
         if (!existingSound.isDone()) {
            if (!existingSound.isStopping()) {
               return;
            }

            existingSound.cancelFadeOut();
            return;
         }

         soundMap.remove(entityId);
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      FadingLoopSound newSound = new FadingLoopSound(sound, SoundCategory.PLAYERS, volume, pitch, entity, fadeIn, fadeOut, 80.0);
      mc.getSoundManager().play(newSound);
      soundMap.put(entityId, newSound);
   }

   private static void stopRemoteLoopSound(Map<Integer, FadingLoopSound> soundMap, int entityId) {
      FadingLoopSound sound = soundMap.get(entityId);
      if (sound != null) {
         if (sound.isDone()) {
            soundMap.remove(entityId);
         } else if (!sound.isStopping()) {
            sound.fadeOut();
         }
      }
   }

   private static void cleanupRemoteSounds(Map<Integer, FadingLoopSound> soundMap, Set<Integer> activeIds) {
      MinecraftClient mc = MinecraftClient.getInstance();
      Iterator<Entry<Integer, FadingLoopSound>> it = soundMap.entrySet().iterator();

      while (it.hasNext()) {
         Entry<Integer, FadingLoopSound> entry = it.next();
         if (!activeIds.contains(entry.getKey())) {
            if (!entry.getValue().isDone()) {
               mc.getSoundManager().stop(entry.getValue());
            }

            it.remove();
         }
      }
   }

   public static void clearAllRemoteSounds() {
      MinecraftClient mc = MinecraftClient.getInstance();

      for (FadingLoopSound sound : remoteFlightSounds.values()) {
         if (!sound.isDone()) {
            mc.getSoundManager().stop(sound);
         }
      }

      remoteFlightSounds.clear();

      for (FadingLoopSound soundx : remoteGasBoostSounds.values()) {
         if (!soundx.isDone()) {
            mc.getSoundManager().stop(soundx);
         }
      }

      remoteGasBoostSounds.clear();
      remotePrevStates.clear();
   }

   @Environment(EnvType.CLIENT)
   private static class LocalOneShot extends AbstractSoundInstance {
      protected LocalOneShot(SoundEvent sound, float volume, float pitch) {
         super(sound, SoundCategory.PLAYERS, SoundInstance.createRandom());
         this.volume = volume;
         this.pitch = pitch;
         this.attenuationType = AttenuationType.NONE;
         this.relative = true;
      }
   }

   @Environment(EnvType.CLIENT)
   private static class RemoteODMState {
      boolean leftActive;
      boolean leftExtending;
      boolean leftRetracting;
      boolean rightActive;
      boolean rightExtending;
      boolean rightRetracting;
   }
}
