package daot;

import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;

@Environment(EnvType.CLIENT)
public final class SoldierboyShakeHandler {
   private static final double MAX_SHAKE_DISTANCE = 150.0;
   private static final float BASE_INTENSITY = 0.3F;
   private static final int REFRESH_TICKS = 3;

   private SoldierboyShakeHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(SoldierboyShakeHandler::tick);
   }

   private static void tick(MinecraftClient mc) {
      ClientPlayerEntity self = mc.player;
      if (self != null && mc.world != null) {
         if (!SoldierboyClientHandler.laserFirers().isEmpty()) {
            for (UUID firerId : SoldierboyClientHandler.laserFirers()) {
               PlayerEntity firer = mc.world.getPlayerByUuid(firerId);
               if (firer != null) {
                  double dist = self.getPos().distanceTo(firer.getPos());
                  if (!(dist > 150.0)) {
                     float falloff = 1.0F - (float)(dist / 150.0);
                     CameraShakeHandler.triggerEarthquakeShake(3, 0.3F * falloff);
                  }
               }
            }
         }
      }
   }
}
