package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundInstance.AttenuationType;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class FadingLoopSound extends MovingSoundInstance {
   private boolean stopping = false;
   private boolean fadingIn = true;
   private final float fadeInSpeed;
   private final float fadeOutSpeed;
   private final float maxVolume;
   private Entity trackedEntity;
   private float fadeFactor = 0.01F;
   private final double maxDistance;
   private final int loopDurationTicks;
   private final int crossfadeTicks;
   private final Runnable onSpawnNext;
   private int ticksAlive = 0;
   private boolean spawnedNext = false;

   public FadingLoopSound(
      SoundEvent sound, SoundCategory source, float volume, float pitch, double x, double y, double z, float fadeInSpeed, float fadeOutSpeed
   ) {
      super(sound, source, SoundInstance.createRandom());
      this.repeat = true;
      this.repeatDelay = 0;
      this.maxVolume = volume;
      this.volume = 0.01F;
      this.pitch = pitch;
      this.x = x;
      this.y = y;
      this.z = z;
      this.attenuationType = AttenuationType.NONE;
      this.relative = true;
      this.fadeInSpeed = fadeInSpeed;
      this.fadeOutSpeed = fadeOutSpeed;
      this.maxDistance = 0.0;
      this.loopDurationTicks = 0;
      this.crossfadeTicks = 0;
      this.onSpawnNext = null;
   }

   public FadingLoopSound(
      SoundEvent sound,
      SoundCategory source,
      float volume,
      float pitch,
      double x,
      double y,
      double z,
      float fadeInSpeed,
      float fadeOutSpeed,
      double maxDistance
   ) {
      super(sound, source, SoundInstance.createRandom());
      this.repeat = true;
      this.repeatDelay = 0;
      this.maxVolume = volume;
      this.volume = 0.01F;
      this.pitch = pitch;
      this.x = x;
      this.y = y;
      this.z = z;
      this.attenuationType = AttenuationType.NONE;
      this.relative = false;
      this.fadeInSpeed = fadeInSpeed;
      this.fadeOutSpeed = fadeOutSpeed;
      this.maxDistance = maxDistance;
      this.loopDurationTicks = 0;
      this.crossfadeTicks = 0;
      this.onSpawnNext = null;
   }

   public FadingLoopSound(
      SoundEvent sound, SoundCategory source, float volume, float pitch, Entity entity, float fadeInSpeed, float fadeOutSpeed, double maxDistance
   ) {
      super(sound, source, SoundInstance.createRandom());
      this.repeat = true;
      this.repeatDelay = 0;
      this.maxVolume = volume;
      this.volume = 0.01F;
      this.pitch = pitch;
      this.x = entity.getX();
      this.y = entity.getY();
      this.z = entity.getZ();
      this.attenuationType = AttenuationType.NONE;
      this.relative = false;
      this.fadeInSpeed = fadeInSpeed;
      this.fadeOutSpeed = fadeOutSpeed;
      this.maxDistance = maxDistance;
      this.trackedEntity = entity;
      this.loopDurationTicks = 0;
      this.crossfadeTicks = 0;
      this.onSpawnNext = null;
   }

   public FadingLoopSound(
      SoundEvent sound,
      SoundCategory source,
      float volume,
      float pitch,
      Entity entity,
      float fadeInSpeed,
      float fadeOutSpeed,
      double maxDistance,
      int loopDurationTicks,
      int crossfadeTicks,
      Runnable onSpawnNext
   ) {
      super(sound, source, SoundInstance.createRandom());
      this.repeat = false;
      this.repeatDelay = 0;
      this.maxVolume = volume;
      this.volume = 0.01F;
      this.pitch = pitch;
      this.x = entity.getX();
      this.y = entity.getY();
      this.z = entity.getZ();
      this.attenuationType = AttenuationType.NONE;
      this.relative = false;
      this.fadeInSpeed = fadeInSpeed;
      this.fadeOutSpeed = fadeOutSpeed;
      this.maxDistance = maxDistance;
      this.trackedEntity = entity;
      this.loopDurationTicks = loopDurationTicks;
      this.crossfadeTicks = crossfadeTicks;
      this.onSpawnNext = onSpawnNext;
   }

   public void setTrackedEntity(Entity entity) {
      this.trackedEntity = entity;
   }

   @Override
   public void tick() {
      if (this.trackedEntity != null) {
         if (this.trackedEntity.isRemoved()) {
            this.setDone();
            return;
         }

         this.x = this.trackedEntity.getX();
         this.y = this.trackedEntity.getY();
         this.z = this.trackedEntity.getZ();
      }

      if (this.stopping) {
         this.fadeFactor = Math.max(0.0F, this.fadeFactor - this.fadeOutSpeed);
         if (this.fadeFactor <= 0.01F) {
            this.volume = 0.0F;
            this.setDone();
            return;
         }
      } else if (this.fadingIn) {
         this.fadeFactor = Math.min(1.0F, this.fadeFactor + this.fadeInSpeed);
         if (this.fadeFactor >= 1.0F) {
            this.fadingIn = false;
         }
      }

      float loopEnvelope = 1.0F;
      if (this.loopDurationTicks > 0) {
         this.ticksAlive++;
         if (!this.spawnedNext && !this.stopping && this.onSpawnNext != null && this.ticksAlive >= this.loopDurationTicks - this.crossfadeTicks) {
            this.spawnedNext = true;
            this.onSpawnNext.run();
         }

         if (this.ticksAlive >= this.loopDurationTicks) {
            this.volume = 0.0F;
            this.setDone();
            return;
         }

         if (this.ticksAlive < this.crossfadeTicks) {
            loopEnvelope = (float)this.ticksAlive / this.crossfadeTicks;
         } else if (this.ticksAlive >= this.loopDurationTicks - this.crossfadeTicks) {
            int into = this.ticksAlive - (this.loopDurationTicks - this.crossfadeTicks);
            loopEnvelope = Math.max(0.0F, 1.0F - (float)into / this.crossfadeTicks);
         }
      }

      float distanceFactor = 1.0F;
      if (this.maxDistance > 0.0) {
         distanceFactor = this.calculateDistanceFactor();
      }

      this.volume = this.maxVolume * this.fadeFactor * distanceFactor * loopEnvelope;
   }

   private float calculateDistanceFactor() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         return 0.0F;
      } else {
         double dist = mc.player.getPos().distanceTo(new Vec3d(this.x, this.y, this.z));
         if (dist >= this.maxDistance) {
            return 0.0F;
         } else {
            double factor = 1.0 - dist / this.maxDistance;
            return (float)(factor * factor);
         }
      }
   }

   @Override
   public boolean shouldAlwaysPlay() {
      return true;
   }

   @Override
   public boolean canPlay() {
      return !this.stopping;
   }

   public void fadeOut() {
      this.stopping = true;
      this.fadingIn = false;
      this.spawnedNext = true;
   }

   public void cancelFadeOut() {
      if (this.stopping) {
         this.stopping = false;
         if (this.fadeFactor < 1.0F) {
            this.fadingIn = true;
         }
      }
   }

   public boolean isStopping() {
      return this.stopping;
   }

   public boolean isFadingIn() {
      return this.fadingIn;
   }
}
