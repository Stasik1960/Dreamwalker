package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class ColossalSteamHandler {
   private static final double STEAM_RADIUS = 60.0;
   private static final double MIN_DISTANCE = 5.0;
   private static final double BASE_PUSH_STRENGTH = 0.35;
   private static final double MAX_PUSH_STRENGTH = 0.8;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null && client.world != null) {
            ClientPlayerEntity player = client.player;

            for (Entity entity : client.world.getOtherEntities(player, player.getBoundingBox().expand(70.0))) {
               if (entity instanceof ColossalTitanEntity colossalTitan && colossalTitan.isSteaming()) {
                  applyClientSidePush(player, colossalTitan);
               }
            }
         }
      });
   }

   private static void applyClientSidePush(ClientPlayerEntity player, ColossalTitanEntity titan) {
      if (player.getVehicle() != titan) {
         double dx = player.getX() - titan.getX();
         double dz = player.getZ() - titan.getZ();
         double horizontalDist = Math.sqrt(dx * dx + dz * dz);
         if (!(horizontalDist > 60.0) && !(horizontalDist < 5.0)) {
            double intensity = 1.0 - horizontalDist / 60.0;
            double pushStrength = 0.35 + 0.45000000000000007 * intensity * intensity;
            double pushX = dx / horizontalDist * pushStrength;
            double pushZ = dz / horizontalDist * pushStrength;
            double pushY = 0.02 * intensity;
            Vec3d currentVel = player.getVelocity();
            player.setVelocity(currentVel.x + pushX, currentVel.y + pushY, currentVel.z + pushZ);
         }
      }
   }
}
