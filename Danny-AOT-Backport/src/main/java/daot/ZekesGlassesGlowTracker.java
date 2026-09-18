package daot;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ZekesGlassesGlowTracker {
   private static final Set<Integer> GLOWING_ENTITY_IDS = Collections.newSetFromMap(new ConcurrentHashMap<>());

   public static void updateGlowingEntities(int[] entityIds) {
      GLOWING_ENTITY_IDS.clear();

      for (int id : entityIds) {
         GLOWING_ENTITY_IDS.add(id);
      }
   }

   public static void clearGlowingEntities() {
      GLOWING_ENTITY_IDS.clear();
   }

   public static boolean shouldGlow(int entityId) {
      return GLOWING_ENTITY_IDS.contains(entityId);
   }
}
