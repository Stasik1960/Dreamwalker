package daot;

import daot.network.SadTitanSprintPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import daot.compat.network.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class SadTitanClientHelper {
   private static boolean wasSprinting = false;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null && client.getNetworkHandler() != null) {
            if (client.player.getVehicle() instanceof SadTitanEntity sad && sad.isRideable) {
               boolean hasInput = Math.abs(client.player.input.movementForward) > 0.01 || Math.abs(client.player.input.movementSideways) > 0.01;
               boolean sprinting = hasInput && client.options.sprintKey.isPressed();
               if (sprinting != wasSprinting) {
                  ClientPlayNetworking.send(new SadTitanSprintPayload(sprinting));
                  wasSprinting = sprinting;
               }
            } else {
               wasSprinting = false;
            }
         }
      });
   }
}
