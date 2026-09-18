package daot;

import daot.network.PlayBitePayload;
import daot.network.PreshiftEffectPayload;
import daot.network.PreshiftStartPayload;
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

@Environment(EnvType.CLIENT)
public class PreshiftEffectTracker {
   private static boolean preshiftActive = false;
   private static int ticksRemaining = 0;
   private static final int TOTAL_DURATION_TICKS = 80;

   public static void register() {
      ClientPlayNetworking.registerGlobalReceiver(
         PreshiftStartPayload.TYPE, (payload, context) -> context.client().execute(() -> onPreshiftConfirmed(payload.playColossalNuke(), payload.tease()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         PreshiftEffectPayload.TYPE,
         (payload, context) -> context.client().execute(() -> onOtherPlayerPreshift(payload.entityId(), payload.playColossalNuke()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         PlayBitePayload.TYPE, (payload, context) -> context.client().execute(() -> ODMAnimationHandler.triggerBiteForPlayer(payload.entityId()))
      );
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               if (preshiftActive) {
                  MinecraftClient mc = MinecraftClient.getInstance();
                  if (mc.player != null && mc.world != null) {
                     Entity vehicle = mc.player.getVehicle();
                     if (!(vehicle instanceof ColossalTitanEntity)
                        && !(vehicle instanceof AttackTitanEntity)
                        && !(vehicle instanceof ArmoredTitanEntity)
                        && !(vehicle instanceof FemaleTitanEntity)
                        && !(vehicle instanceof BeastTitanEntity)
                        && !(vehicle instanceof WarhammerTitanEntity)) {
                        ticksRemaining--;
                        if (ticksRemaining <= 0) {
                           ShiftParticleHelper.stopPreshiftEffect();
                           stopAllEffects();
                        }
                     } else {
                        ShiftParticleHelper.stopPreshiftEffect();
                        TitanShiftHandler.onTitanMounted();
                        stopAllEffects();
                        DannysAot.LOGGER.debug("Player mounted titan - preshift complete");
                     }
                  } else {
                     stopAllEffects();
                  }
               }
            }
         );
   }

   private static void onOtherPlayerPreshift(int entityId, boolean playColossalNuke) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null) {
         Entity entity = mc.world.getEntityById(entityId);
         if (entity == null) {
            DannysAot.LOGGER.debug("Could not find entity {} for preshift effect", entityId);
         } else {
            ShiftParticleHelper.spawnPreshiftParticleBoundToEntity(mc.world, entity);
            if (playColossalNuke) {
               ShiftParticleHelper.spawnColossalNukeParticle(mc.world, entity.getX(), entity.getY() - 3.0, entity.getZ());
            }

            PositionedSoundInstance handbiteSound = new PositionedSoundInstance(
               ModSounds.HANDBITE, SoundCategory.PLAYERS, 1.0F, 1.0F, mc.world.random, entity.getX(), entity.getY(), entity.getZ()
            );
            mc.getSoundManager().play(handbiteSound);
            PositionedSoundInstance chargeupSound = new PositionedSoundInstance(
               ModSounds.CHARGEUP, SoundCategory.PLAYERS, 1.0F, 1.0F, mc.world.random, entity.getX(), entity.getY(), entity.getZ()
            );
            mc.getSoundManager().play(chargeupSound);
            PositionedSoundInstance bellSound = new PositionedSoundInstance(
               SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 2.0F, 1.0F, mc.world.random, entity.getX(), entity.getY(), entity.getZ()
            );
            mc.getSoundManager().play(bellSound);
            DannysAot.LOGGER.debug("Spawned preshift effect and sounds for other player entity {}", entityId);
         }
      }
   }

   private static void onPreshiftConfirmed(boolean playColossalNuke, boolean tease) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.world != null) {
         Entity currentVehicle = mc.player.getVehicle();
         if (!(currentVehicle instanceof ColossalTitanEntity)
            && !(currentVehicle instanceof AttackTitanEntity)
            && !(currentVehicle instanceof ArmoredTitanEntity)
            && !(currentVehicle instanceof FemaleTitanEntity)
            && !(currentVehicle instanceof BeastTitanEntity)
            && !(currentVehicle instanceof WarhammerTitanEntity)) {
            preshiftActive = true;
            ticksRemaining = 80;
            if (!tease) {
               TitanShiftHandler.onPreshiftStart();
            }

            ShiftParticleHelper.spawnPreshiftParticleBoundToEntity(mc.world, mc.player);
            if (playColossalNuke) {
               ShiftParticleHelper.spawnColossalNukeParticle(mc.world, mc.player.getX(), mc.player.getY() - 3.0, mc.player.getZ());
            }

            PositionedSoundInstance handbiteSound = new PositionedSoundInstance(
               ModSounds.HANDBITE, SoundCategory.PLAYERS, 1.0F, 1.0F, mc.world.random, mc.player.getX(), mc.player.getY(), mc.player.getZ()
            );
            mc.getSoundManager().play(handbiteSound);
            PositionedSoundInstance chargeupSound = new PositionedSoundInstance(
               ModSounds.CHARGEUP, SoundCategory.PLAYERS, 1.0F, 1.0F, mc.world.random, mc.player.getX(), mc.player.getY(), mc.player.getZ()
            );
            mc.getSoundManager().play(chargeupSound);
            PositionedSoundInstance bellSound = new PositionedSoundInstance(
               SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 2.0F, 1.0F, mc.world.random, mc.player.getX(), mc.player.getY(), mc.player.getZ()
            );
            mc.getSoundManager().play(bellSound);
            DannysAot.LOGGER.debug("Preshift started - effects and sounds playing");
         }
      }
   }

   private static void stopAllEffects() {
      preshiftActive = false;
      ticksRemaining = 0;
      DannysAot.LOGGER.debug("All transformation effects stopped");
   }

   public static boolean isActive() {
      return preshiftActive;
   }
}
