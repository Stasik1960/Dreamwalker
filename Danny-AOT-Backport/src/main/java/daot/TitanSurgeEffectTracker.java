package daot;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

@Environment(EnvType.CLIENT)
public final class TitanSurgeEffectTracker {
   private static final int EFFECT_TICKS = 40;
   private static final Map<Integer, Integer> remaining = new HashMap<>();

   private TitanSurgeEffectTracker() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (!remaining.isEmpty()) {
            Iterator<Entry<Integer, Integer>> it = remaining.entrySet().iterator();

            while (it.hasNext()) {
               Entry<Integer, Integer> e = it.next();
               int left = e.getValue() - 1;
               if (left <= 0) {
                  ShiftParticleHelper.stopTitanSurgeEffect(e.getKey());
                  it.remove();
               } else {
                  e.setValue(left);
               }
            }
         }
      });
   }

   public static void play(Entity entity) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && entity != null) {
         ShiftParticleHelper.spawnTitanSurgeBoundToEntity(mc.world, entity);
         remaining.put(entity.getId(), 40);
         playSound(mc, ModSounds.HANDBITE, 1.0F, entity);
         playSound(mc, ModSounds.CHARGEUP, 1.0F, entity);
         playSound(mc, SoundEvents.BLOCK_BELL_RESONATE, 2.0F, entity);
      }
   }

   public static void playRemote(int entityId) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null) {
         Entity entity = mc.world.getEntityById(entityId);
         if (entity == null) {
            DannysAot.LOGGER.debug("Could not find entity {} for titan_surge effect", entityId);
         } else if (mc.player == null || entityId != mc.player.getId()) {
            play(entity);
         }
      }
   }

   public static void stop(int entityId) {
      if (remaining.remove(entityId) != null) {
         ShiftParticleHelper.stopTitanSurgeEffect(entityId);
      }
   }

   public static void reset() {
      remaining.clear();
      ShiftParticleHelper.stopAllTitanSurgeEffects();
   }

   private static void playSound(MinecraftClient mc, SoundEvent sound, float volume, Entity at) {
      mc.getSoundManager().play(new PositionedSoundInstance(sound, SoundCategory.PLAYERS, volume, 1.0F, mc.world.random, at.getX(), at.getY(), at.getZ()));
   }
}
