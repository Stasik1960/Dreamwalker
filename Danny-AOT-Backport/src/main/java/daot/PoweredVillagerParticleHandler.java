package daot;

import daot.network.PoweredVillagerSyncPayload;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import daot.compat.network.ClientPlayNetworking.Context;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.DustParticleEffect;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class PoweredVillagerParticleHandler {
   private static final Map<Integer, Integer> poweredVillagers = new ConcurrentHashMap<>();
   private static final Vector3f ATTACK_COLOR = new Vector3f(0.2F, 0.9F, 0.2F);
   private static final Vector3f COLOSSAL_COLOR = new Vector3f(0.9F, 0.2F, 0.2F);
   private static final Vector3f ARMORED_COLOR = new Vector3f(0.9F, 0.5F, 0.1F);
   private static final Vector3f BEAST_COLOR = new Vector3f(0.55F, 0.35F, 0.15F);
   private static final Vector3f FEMALE_COLOR = new Vector3f(1.0F, 0.4F, 0.7F);
   private static final Vector3f WARHAMMER_COLOR = new Vector3f(1.0F, 1.0F, 1.0F);
   private static final Vector3f JAW_COLOR = new Vector3f(0.9F, 0.5F, 0.1F);
   private static final DustParticleEffect ATTACK_PARTICLE = new DustParticleEffect(ATTACK_COLOR, 1.0F);
   private static final DustParticleEffect COLOSSAL_PARTICLE = new DustParticleEffect(COLOSSAL_COLOR, 1.0F);
   private static final DustParticleEffect ARMORED_PARTICLE = new DustParticleEffect(ARMORED_COLOR, 1.0F);
   private static final DustParticleEffect BEAST_PARTICLE = new DustParticleEffect(BEAST_COLOR, 1.0F);
   private static final DustParticleEffect FEMALE_PARTICLE = new DustParticleEffect(FEMALE_COLOR, 1.0F);
   private static final DustParticleEffect WARHAMMER_PARTICLE = new DustParticleEffect(WARHAMMER_COLOR, 1.0F);
   private static final DustParticleEffect JAW_PARTICLE = new DustParticleEffect(JAW_COLOR, 1.0F);

   public static void tick(MinecraftClient mc) {
      if (mc.world != null && !mc.isPaused()) {
         ClientWorld level = mc.world;

         for (Entry<Integer, Integer> entry : poweredVillagers.entrySet()) {
            int entityId = entry.getKey();
            int powerOrdinal = entry.getValue();
            if (level.getEntityById(entityId) instanceof VillagerEntity villager && villager.isAlive()) {
               spawnPowerParticles(level, villager, powerOrdinal);
            }
         }
      }
   }

   private static void spawnPowerParticles(ClientWorld level, VillagerEntity villager, int powerOrdinal) {
      DustParticleEffect particle = switch (powerOrdinal) {
         case 0 -> ATTACK_PARTICLE;
         default -> COLOSSAL_PARTICLE;
         case 2 -> ARMORED_PARTICLE;
         case 3 -> BEAST_PARTICLE;
         case 4 -> FEMALE_PARTICLE;
         case 5 -> WARHAMMER_PARTICLE;
         case 6 -> JAW_PARTICLE;
      };
      double x = villager.getX();
      double y = villager.getY() + villager.getHeight() * 0.5;
      double z = villager.getZ();

      for (int i = 0; i < 4; i++) {
         double offsetX = (level.random.nextDouble() - 0.5) * 0.8;
         double offsetY = (level.random.nextDouble() - 0.5) * 1.2;
         double offsetZ = (level.random.nextDouble() - 0.5) * 0.8;
         level.addParticle(particle, x + offsetX, y + offsetY, z + offsetZ, 0.0, 0.02, 0.0);
      }
   }

   public static void handleSync(PoweredVillagerSyncPayload payload, Context context) {
      context.client().execute(() -> {
         if (payload.fullSync()) {
            poweredVillagers.clear();

            for (Entry<Integer, Integer> entry : payload.poweredVillagers().entrySet()) {
               if (entry.getValue() >= 0) {
                  poweredVillagers.put(entry.getKey(), entry.getValue());
               }
            }
         } else {
            for (Entry<Integer, Integer> entryx : payload.poweredVillagers().entrySet()) {
               if (entryx.getValue() < 0) {
                  poweredVillagers.remove(entryx.getKey());
               } else {
                  poweredVillagers.put(entryx.getKey(), entryx.getValue());
               }
            }
         }
      });
   }

   public static void clear() {
      poweredVillagers.clear();
   }

   public static boolean isPowered(int entityId) {
      return poweredVillagers.containsKey(entityId);
   }

   public static int getPowerType(int entityId) {
      return poweredVillagers.getOrDefault(entityId, -1);
   }
}
