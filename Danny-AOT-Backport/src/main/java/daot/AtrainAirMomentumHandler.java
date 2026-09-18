package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class AtrainAirMomentumHandler {
   private static final double VANILLA_AIR_FRICTION = 0.91;
   private static final double MAX_AIR_HORIZ_VEL = 3.0;

   private AtrainAirMomentumHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(AtrainAirMomentumHandler::tick);
   }

   private static void tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         if (BloodlineClientData.get(player.getUuid()) == BloodlineType.ATRAIN) {
            if (AtrainSpeedClientState.isActive(player.getUuid())) {
               if (!player.isOnGround()) {
                  if (!player.isTouchingWater() && !player.isInLava()) {
                     if (!player.isFallFlying()) {
                        if (!player.getAbilities().flying) {
                           Vec3d d = player.getVelocity();
                           double newX = d.x / 0.91;
                           double newZ = d.z / 0.91;
                           double speedSq = newX * newX + newZ * newZ;
                           if (speedSq > 9.0) {
                              double scale = 3.0 / Math.sqrt(speedSq);
                              newX *= scale;
                              newZ *= scale;
                           }

                           player.setVelocity(newX, d.y, newZ);
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
