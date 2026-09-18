package daot;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class ButcherTentacleClientHandler {
   public static final int DURATION_TICKS = 22;
   public static final int EXTEND_TICKS = 8;
   public static final int HOLD_TICKS = 6;
   public static final int RETRACT_TICKS = 8;
   private static final Map<UUID, ButcherTentacleClientHandler.Activation> ACTIVE = new ConcurrentHashMap<>();

   private ButcherTentacleClientHandler() {
   }

   public static void start(UUID firerId, long startTick, double dirX, double dirY, double dirZ) {
      Vec3d dir = new Vec3d(dirX, dirY, dirZ);
      if (!(dir.lengthSquared() < 1.0E-6)) {
         ACTIVE.put(firerId, new ButcherTentacleClientHandler.Activation(startTick, dir.normalize()));
      }
   }

   public static Set<UUID> activeFirers() {
      pruneExpired();
      return ACTIVE.keySet();
   }

   public static long startTickFor(UUID firerId) {
      ButcherTentacleClientHandler.Activation a = ACTIVE.get(firerId);
      return a == null ? -1L : a.startTick;
   }

   public static Vec3d directionFor(UUID firerId) {
      ButcherTentacleClientHandler.Activation a = ACTIVE.get(firerId);
      return a == null ? null : a.dir;
   }

   public static float extensionProgress(UUID firerId, float partialTick) {
      ButcherTentacleClientHandler.Activation a = ACTIVE.get(firerId);
      if (a == null) {
         return 0.0F;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world == null) {
            return 0.0F;
         } else {
            double elapsed = (float)mc.world.getTime() + partialTick - (float)a.startTick;
            if (elapsed < 0.0) {
               return 0.0F;
            } else if (elapsed < 8.0) {
               float t = (float)(elapsed / 8.0);
               return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            } else if (elapsed < 14.0) {
               return 1.0F;
            } else if (elapsed < 22.0) {
               float t = (float)((elapsed - 8.0 - 6.0) / 8.0);
               return 1.0F - t * t * t;
            } else {
               return 0.0F;
            }
         }
      }
   }

   private static void pruneExpired() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world == null) {
         ACTIVE.clear();
      } else {
         long now = mc.world.getTime();
         ACTIVE.entrySet().removeIf(e -> now - e.getValue().startTick >= 22L);
      }
   }

   public static void clearAll() {
      ACTIVE.clear();
   }

   @Environment(EnvType.CLIENT)
   private record Activation(long startTick, Vec3d dir) {
   }
}
