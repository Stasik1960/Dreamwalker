package daot;

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class FogClientState {
   private static volatile boolean active = false;
   private static volatile Set<Integer> breachedWalls = Set.of();
   private static volatile Set<Integer> breachedDistricts = Set.of();

   public static boolean isActive() {
      return active;
   }

   public static void set(boolean fogActive, int[] walls, int[] districts) {
      active = fogActive;
      Set<Integer> w = new HashSet<>();

      for (int v : walls) {
         w.add(v);
      }

      Set<Integer> d = new HashSet<>();

      for (int v : districts) {
         d.add(v);
      }

      breachedWalls = w;
      breachedDistricts = d;
   }

   public static boolean isSpawnZone(int x, int z) {
      return BreachManager.isSpawnZone(x, z, breachedWalls, breachedDistricts);
   }
}
