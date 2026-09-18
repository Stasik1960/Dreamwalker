package daot;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class TargetGlowTracker {
   private static final Set<Integer> GLOWING = Collections.newSetFromMap(new ConcurrentHashMap<>());

   public static void update(int[] ids) {
      GLOWING.clear();

      for (int id : ids) {
         GLOWING.add(id);
      }
   }

   public static void clear() {
      GLOWING.clear();
   }

   public static boolean shouldGlow(int entityId) {
      return GLOWING.contains(entityId);
   }
}
