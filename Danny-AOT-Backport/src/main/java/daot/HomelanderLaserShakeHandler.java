package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class HomelanderLaserShakeHandler {
   private HomelanderLaserShakeHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(HomelanderLaserShakeHandler::tick);
   }

   private static void tick(MinecraftClient mc) {
      ClientPlayerEntity self = mc.player;
      if (self != null) {
         if (HomelanderLaserClientHandler.activeFirers().contains(self.getUuid())) {
            CameraShakeHandler.pingLaserShake();
         }
      }
   }
}
