package daot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.math.BlockPos;

public final class StrwsAimServerState {
   private static final Map<UUID, BlockPos> CONTROLLING = new ConcurrentHashMap<>();

   private StrwsAimServerState() {
   }

   public static void put(UUID player, BlockPos controller) {
      CONTROLLING.put(player, controller);
   }

   public static BlockPos get(UUID player) {
      return CONTROLLING.get(player);
   }

   public static void clear(UUID player) {
      CONTROLLING.remove(player);
   }
}
