package daot;

import daot.network.VillagerTransformPayload;
import daot.network.VillagerTransformSpawnPayload;
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
public class VillagerTransformEffectHandler {
   private static final double MAX_SOUND_DISTANCE = 1000.0;
   private static final double SOUND_DELAY_TICKS_PER_BLOCK = 0.1;
   private static int pendingSoundDelayTicks = 0;
   private static double pendingSoundX = 0.0;
   private static double pendingSoundY = 0.0;
   private static double pendingSoundZ = 0.0;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(
         VillagerTransformPayload.TYPE, (payload, context) -> context.client().execute(() -> onVillagerTransformStart(payload.villagerEntityId()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         VillagerTransformSpawnPayload.TYPE,
         (payload, context) -> context.client().execute(() -> onTitanSpawnFromVillager(payload.x(), payload.y(), payload.z(), payload.titanEntityId()))
      );
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> tickDelayedSounds());
      DannysAot.LOGGER.info("Registered VillagerTransformEffectHandler");
   }

   private static void onVillagerTransformStart(int villagerEntityId) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null) {
         Entity villager = mc.world.getEntityById(villagerEntityId);
         if (villager == null) {
            DannysAot.LOGGER.debug("Could not find villager entity {} for transform effects", villagerEntityId);
         } else {
            ShiftParticleHelper.spawnPreshiftParticleBoundToEntity(mc.world, villager);
            PositionedSoundInstance handbiteSound = new PositionedSoundInstance(
               ModSounds.HANDBITE, SoundCategory.HOSTILE, 1.0F, 1.0F, mc.world.random, villager.getX(), villager.getY(), villager.getZ()
            );
            mc.getSoundManager().play(handbiteSound);
            PositionedSoundInstance chargeupSound = new PositionedSoundInstance(
               ModSounds.CHARGEUP, SoundCategory.HOSTILE, 1.0F, 1.0F, mc.world.random, villager.getX(), villager.getY(), villager.getZ()
            );
            mc.getSoundManager().play(chargeupSound);
            PositionedSoundInstance bellSound = new PositionedSoundInstance(
               SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 2.0F, 1.0F, mc.world.random, villager.getX(), villager.getY(), villager.getZ()
            );
            mc.getSoundManager().play(bellSound);
            DannysAot.LOGGER.debug("Started villager transform effects for entity {}", villagerEntityId);
         }
      }
   }

   private static void onTitanSpawnFromVillager(double x, double y, double z, int titanEntityId) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null) {
         ShiftParticleHelper.spawnShiftParticle(mc.world, x, y, z, 0.25F);
         Vec3d playerPos = mc.player.getPos();
         double distance = playerPos.distanceTo(new Vec3d(x, y, z));
         if (distance > 1000.0) {
            DannysAot.LOGGER.debug("Titan spawn too far for sound: {} blocks", distance);
         } else {
            int delayTicks = (int)(distance * 0.1);
            if (delayTicks <= 1) {
               playTitanShiftSound(x, y, z, distance);
            } else {
               pendingSoundDelayTicks = delayTicks;
               pendingSoundX = x;
               pendingSoundY = y;
               pendingSoundZ = z;
            }

            DannysAot.LOGGER.debug("Titan spawned from villager at ({}, {}, {}), sound delay: {} ticks", new Object[]{x, y, z, delayTicks});
         }
      }
   }

   private static void tickDelayedSounds() {
      if (pendingSoundDelayTicks > 0) {
         pendingSoundDelayTicks--;
         if (pendingSoundDelayTicks <= 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
               Vec3d playerPos = mc.player.getPos();
               double distance = playerPos.distanceTo(new Vec3d(pendingSoundX, pendingSoundY, pendingSoundZ));
               playTitanShiftSound(pendingSoundX, pendingSoundY, pendingSoundZ, distance);
            }
         }
      }
   }

   private static void playTitanShiftSound(double x, double y, double z, double distance) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null) {
         float volume = (float)(10.0 * Math.max(0.1, 1.0 / (1.0 + distance / 100.0)));
         PositionedSoundInstance shiftSound = new PositionedSoundInstance(ModSounds.TITAN_SHIFT, SoundCategory.HOSTILE, volume, 1.0F, mc.world.random, x, y, z);
         mc.getSoundManager().play(shiftSound);
         PositionedSoundInstance thunderSound = new PositionedSoundInstance(
            SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.HOSTILE, 3.0F, 0.5F, mc.world.random, x, y, z
         );
         mc.getSoundManager().play(thunderSound);
         DannysAot.LOGGER.debug("Played titan shift sound at ({}, {}, {}) with volume {}", new Object[]{x, y, z, volume});
      }
   }
}
