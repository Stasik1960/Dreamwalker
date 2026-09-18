package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public class BloodmoonMusicHandler {
   private static final RegistryKey<World> PARADIS = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));
   private static final int FADE_DURATION_TICKS = 60;
   private static volatile boolean active = false;
   private static volatile int currentTrack = 0;
   private static BloodmoonMusicHandler.BloodmoonMusicInstance currentInstance;

   private static float maxVolumeFor(int trackIndex) {
      return trackIndex == 0 ? 0.5F : 1.0F;
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)c -> tick());
   }

   public static void setActive(boolean v) {
      active = v;
      if (!v && currentInstance != null) {
         currentInstance.startFadeOut();
      }
   }

   public static void setTrack(int index) {
      currentTrack = index;
      if (active && isInParadis()) {
         startTrack(index);
      }
   }

   private static void tick() {
      boolean paradis = isInParadis();
      if (active && paradis) {
         if (currentInstance == null || currentInstance.isDone() || currentInstance.getTrackIndex() != currentTrack) {
            startTrack(currentTrack);
         }
      } else {
         if (currentInstance != null && !currentInstance.isFading() && !currentInstance.isDone()) {
            currentInstance.startFadeOut();
         }
      }
   }

   private static boolean isInParadis() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity p = mc.player;
      return p != null && p.getWorld().getRegistryKey() == PARADIS;
   }

   private static void startTrack(int idx) {
      SoundEvent event = trackEvent(idx);
      if (event != null) {
         if (currentInstance != null) {
            MinecraftClient.getInstance().getSoundManager().stop(currentInstance);
         }

         BloodmoonMusicHandler.BloodmoonMusicInstance inst = new BloodmoonMusicHandler.BloodmoonMusicInstance(event, idx);
         currentInstance = inst;
         MinecraftClient.getInstance().getSoundManager().play(inst);
      }
   }

   private static SoundEvent trackEvent(int idx) {
      return switch (idx) {
         case 0 -> ModSounds.BLOODMOON_1;
         case 1 -> ModSounds.BLOODMOON_2;
         case 2 -> ModSounds.BLOODMOON_3;
         case 3 -> ModSounds.BLOODMOON_4;
         default -> null;
      };
   }

   @Environment(EnvType.CLIENT)
   private static class BloodmoonMusicInstance extends MovingSoundInstance {
      private final int trackIndex;
      private final float maxVolume;
      private boolean fading = false;
      private int fadeTicksLeft = 60;

      BloodmoonMusicInstance(SoundEvent event, int trackIndex) {
         super(event, SoundCategory.MASTER, Random.create());
         this.trackIndex = trackIndex;
         this.maxVolume = BloodmoonMusicHandler.maxVolumeFor(trackIndex);
         this.volume = this.maxVolume;
         this.pitch = 1.0F;
         this.repeat = false;
         this.relative = true;
      }

      int getTrackIndex() {
         return this.trackIndex;
      }

      boolean isFading() {
         return this.fading;
      }

      void startFadeOut() {
         if (!this.fading) {
            this.fading = true;
            this.fadeTicksLeft = 60;
         }
      }

      @Override
      public void tick() {
         if (this.fading) {
            this.fadeTicksLeft--;
            this.volume = Math.max(0.0F, this.maxVolume * this.fadeTicksLeft / 60.0F);
            if (this.fadeTicksLeft <= 0) {
               this.setDone();
            }
         }
      }
   }
}
