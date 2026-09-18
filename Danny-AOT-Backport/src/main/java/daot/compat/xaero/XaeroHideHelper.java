package daot.compat.xaero;

import daot.HoodTracker;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

@Environment(EnvType.CLIENT)
public final class XaeroHideHelper {
   private XaeroHideHelper() {
   }

   public static boolean shouldHide(UUID playerId) {
      if (playerId == null) {
         return false;
      } else if (HoodTracker.isHoodUpClient(playerId)) {
         return true;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world == null) {
            return false;
         } else {
            PlayerEntity player = mc.world.getPlayerByUuid(playerId);
            return player != null && shouldHidePlayer(player);
         }
      }
   }

   public static boolean shouldHidePlayer(PlayerEntity player) {
      return HoodTracker.isHoodUpClient(player.getUuid()) ? true : player.isInvisible() && player.hasVehicle();
   }
}
