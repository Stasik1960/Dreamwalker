package daot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class TestShifterCameraAnchor {
   private static final Map<Integer, Vec3d> byEntityId = new ConcurrentHashMap<>();

   private TestShifterCameraAnchor() {
   }

   public static void set(int entityId, Vec3d worldPos) {
      byEntityId.put(entityId, worldPos);
   }

   public static void clear(int entityId) {
      byEntityId.remove(entityId);
   }

   public static Vec3d get(int entityId) {
      return byEntityId.get(entityId);
   }
}
