package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public final class StrwsAimClientState {
   public static BlockPos controlledPos = null;

   private StrwsAimClientState() {
   }

   public static boolean isAiming() {
      return controlledPos != null;
   }

   public static void start(BlockPos pos) {
      controlledPos = pos;
   }

   public static void stop() {
      controlledPos = null;
   }
}
