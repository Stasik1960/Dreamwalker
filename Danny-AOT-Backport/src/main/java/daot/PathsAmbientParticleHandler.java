package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

@Environment(EnvType.CLIENT)
public class PathsAmbientParticleHandler {
   private static final Identifier PATHS_DIMENSION_ID = new Identifier("dannys-aot", "paths");
   private static final double PATHS_EFFECT_X = 0.0;
   private static final double PATHS_EFFECT_Y = 64.0;
   private static final double PATHS_EFFECT_Z = 200.0;
   private static final Random random = Random.create();
   private static int tickCounter = 0;
   private static int ambientSoundCounter = 0;
   private static boolean pathsEffectSpawned = false;
   private static boolean wasInPathsDimension = false;
   private static final int SPAWN_INTERVAL = 40;
   private static final int AMBIENT_SOUND_INTERVAL = 200;
   private static final SoundEvent NETHER_WASTES_AMBIENT = SoundEvent.of(new Identifier("minecraft:ambient.nether_wastes.loop"));

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null && client.world != null) {
            boolean inPathsDimension = client.world.getRegistryKey().getValue().equals(PATHS_DIMENSION_ID);
            if (!inPathsDimension) {
               if (wasInPathsDimension) {
                  ShiftParticleHelper.stopPathsEffect();
                  wasInPathsDimension = false;
                  pathsEffectSpawned = false;
               }
            } else {
               wasInPathsDimension = true;
               tickCounter++;
               ambientSoundCounter++;
               if (tickCounter >= 40) {
                  tickCounter = 0;
                  spawnAmbientParticles(client, client.player);
               }

               if (!pathsEffectSpawned) {
                  pathsEffectSpawned = true;
                  spawnPathsEffect(client);
                  DannysAot.LOGGER.info("Spawned paths effect at origin (one-time spawn)");
               }

               if (ambientSoundCounter >= 200) {
                  ambientSoundCounter = 0;
                  playAmbientSound(client, client.player);
               }
            }
         } else {
            if (wasInPathsDimension) {
               ShiftParticleHelper.stopPathsEffect();
               wasInPathsDimension = false;
               pathsEffectSpawned = false;
            }
         }
      });
      DannysAot.LOGGER.info("Registered Paths ambient particle handler");
   }

   private static void spawnAmbientParticles(MinecraftClient client, ClientPlayerEntity player) {
      if (ShiftParticleHelper.isAAAParticlesAvailable()) {
         double offsetX = (random.nextDouble() - 0.5) * 32.0;
         double offsetY = (random.nextDouble() - 0.5) * 6.0;
         double offsetZ = (random.nextDouble() - 0.5) * 32.0;
         double x = player.getX() + offsetX;
         double y = player.getY() + offsetY;
         double z = player.getZ() + offsetZ;
         float scale = 0.5F + random.nextFloat();
         ShiftParticleHelper.spawnDustAmbientParticle(client.world, x, y, z, scale);
      }
   }

   private static void spawnPathsEffect(MinecraftClient client) {
      if (ShiftParticleHelper.isAAAParticlesAvailable()) {
         ShiftParticleHelper.spawnPathsParticle(client.world, 0.0, 64.0, 200.0, 1.0F);
      }
   }

   private static void playAmbientSound(MinecraftClient client, ClientPlayerEntity player) {
      PositionedSoundInstance sound = new PositionedSoundInstance(
         NETHER_WASTES_AMBIENT, SoundCategory.AMBIENT, 0.5F, 0.3F, random, player.getX(), player.getY(), player.getZ()
      );
      client.getSoundManager().play(sound);
   }
}
