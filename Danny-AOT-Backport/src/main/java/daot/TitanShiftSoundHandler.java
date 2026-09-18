package daot;

import daot.network.TitanSpawnPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class TitanShiftSoundHandler {
   private static final List<TitanShiftSoundHandler.PendingSound> pendingSounds = new ArrayList<>();
   private static final double SOUND_SPEED_BLOCKS_PER_TICK = 10.0;
   private static final double MAX_SOUND_DISTANCE = 1000.0;
   private static final int MAX_DELAY_TICKS = 100;
   private static final List<TitanShiftSoundHandler.PendingSmokeEffect> pendingSmokeEffects = new ArrayList<>();

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(
         TitanSpawnPayload.TYPE,
         (payload, context) -> context.client()
            .execute(() -> onTitanSpawn(payload.x(), payload.y(), payload.z(), payload.titanEntityId(), payload.titanType()))
      );
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null && client.world != null) {
            pendingSounds.removeIf(pending -> {
               pending.ticksRemaining--;
               if (pending.ticksRemaining <= 0) {
                  playTitanShiftSound(pending.x, pending.y, pending.z, pending.titanType);
                  return true;
               } else {
                  return false;
               }
            });
            pendingSmokeEffects.removeIf(pending -> {
               Entity titanEntity = client.world.getEntityById(pending.titanEntityId);
               if (titanEntity != null) {
                  ShiftParticleHelper.spawnSmokeParticleBoundToEntity(client.world, titanEntity);
                  DannysAot.LOGGER.debug("Spawned delayed smoke effect bound to titan {}", pending.titanEntityId);
                  return true;
               } else {
                  pending.ticksRemaining--;
                  if (pending.ticksRemaining <= 0) {
                     DannysAot.LOGGER.warn("Gave up waiting for titan entity {} for smoke effect", pending.titanEntityId);
                     return true;
                  } else {
                     return false;
                  }
               }
            });
         } else {
            pendingSounds.clear();
            pendingSmokeEffects.clear();
         }
      });
      DannysAot.LOGGER.info("Registered TitanShiftSoundHandler for distance-delayed sounds");
   }

   private static void onTitanSpawn(double spawnX, double spawnY, double spawnZ, int titanEntityId, int titanType) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null && client.world != null) {
         if (titanType != 6) {
            ShiftParticleHelper.spawnShiftParticle(client.world, spawnX, spawnY + 1.0, spawnZ);
            DannysAot.LOGGER.debug("Spawned shift effect at titan spawn position ({}, {}, {})", new Object[]{(int)spawnX, (int)spawnY, (int)spawnZ});
         }

         ShiftParticleHelper.stopPreshiftEffect();
         if (PreshiftEffectTracker.isActive()) {
            TitanShiftHandler.onTitanSpawned();
         }

         Vec3d playerPos = client.player.getPos();
         Vec3d spawnPos = new Vec3d(spawnX, spawnY, spawnZ);
         double distance = playerPos.distanceTo(spawnPos);
         if (!(distance > 1000.0)) {
            int delayTicks = (int)Math.min(distance / 10.0, 100.0);
            if (delayTicks <= 2) {
               playTitanShiftSound(spawnX, spawnY, spawnZ, titanType);
            } else {
               pendingSounds.add(new TitanShiftSoundHandler.PendingSound(spawnX, spawnY, spawnZ, delayTicks, titanType));
               DannysAot.LOGGER.debug("Scheduled titan shift sound with {} tick delay (distance: {} blocks)", delayTicks, (int)distance);
            }
         }
      }
   }

   private static void playTitanShiftSound(double x, double y, double z, int titanType) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null && client.world != null) {
         Vec3d playerPos = client.player.getPos();
         double distance = playerPos.distanceTo(new Vec3d(x, y, z));
         float baseVolume = 15.0F;
         float distanceAttenuation = (float)Math.max(0.1, 1.0 / (1.0 + distance / 100.0));
         float finalVolume = baseVolume * distanceAttenuation;
         float shiftPitch;
         if (titanType == 2) {
            shiftPitch = 0.75F;
         } else {
            shiftPitch = 0.8F + (float)(Math.random() * 0.2);
         }

         PositionedSoundInstance shiftSound = new PositionedSoundInstance(
            ModSounds.TITAN_SHIFT, SoundCategory.HOSTILE, finalVolume, shiftPitch, client.world.random, x, y, z
         );
         client.getSoundManager().play(shiftSound);
         PositionedSoundInstance thunderSound = new PositionedSoundInstance(
            SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.HOSTILE, 3.0F, 0.5F, client.world.random, x, y, z
         );
         client.getSoundManager().play(thunderSound);
         if (titanType == 1) {
            PositionedSoundInstance roarSound = new PositionedSoundInstance(
               ModSounds.ATTACK_TITAN_ROAR, SoundCategory.HOSTILE, finalVolume, 0.9F + (float)(Math.random() * 0.2), client.world.random, x, y, z
            );
            client.getSoundManager().play(roarSound);
            DannysAot.LOGGER.debug("Played Attack Titan roar sound");
         } else if (titanType == 3) {
            PositionedSoundInstance roarSound = new PositionedSoundInstance(
               ModSounds.ATTACK_TITAN_ROAR, SoundCategory.HOSTILE, finalVolume, 1.3F, client.world.random, x, y, z
            );
            client.getSoundManager().play(roarSound);
            DannysAot.LOGGER.debug("Played Female Titan roar sound");
         } else if (titanType == 2) {
            PositionedSoundInstance roarSound = new PositionedSoundInstance(
               ModSounds.ARMORED_TITAN_ROAR, SoundCategory.HOSTILE, finalVolume * 4.0F, 0.9F + (float)(Math.random() * 0.2), client.world.random, x, y, z
            );
            client.getSoundManager().play(roarSound);
            DannysAot.LOGGER.debug("Played Armored Titan roar sound");
         }

         DannysAot.LOGGER
            .debug(
               "Played titan shift sound at ({}, {}, {}) with volume {} (distance: {} blocks)",
               new Object[]{(int)x, (int)y, (int)z, finalVolume, (int)distance}
            );
      }
   }

   @Environment(EnvType.CLIENT)
   private static class PendingSmokeEffect {
      final int titanEntityId;
      int ticksRemaining;

      PendingSmokeEffect(int titanEntityId, int ticksRemaining) {
         this.titanEntityId = titanEntityId;
         this.ticksRemaining = ticksRemaining;
      }
   }

   @Environment(EnvType.CLIENT)
   private static class PendingSound {
      final double x;
      final double y;
      final double z;
      final int titanType;
      int ticksRemaining;

      PendingSound(double x, double y, double z, int ticksRemaining, int titanType) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.ticksRemaining = ticksRemaining;
         this.titanType = titanType;
      }
   }
}
