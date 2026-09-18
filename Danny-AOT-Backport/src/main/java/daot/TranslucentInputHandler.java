package daot;

import daot.network.TranslucentTogglePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;

@Environment(EnvType.CLIENT)
public final class TranslucentInputHandler {
   private static boolean wasRKeyDown = false;
   private static boolean localToggled = false;

   private TranslucentInputHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(TranslucentInputHandler::tick);
   }

   private static void tick(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null) {
         wasRKeyDown = false;
         localToggled = false;
      } else if (BloodlineClientData.get(player.getUuid()) != BloodlineType.TRANSLUCENT) {
         wasRKeyDown = false;
         localToggled = false;
      } else if (ModEffects.isPowerDisabled(player)) {
         wasRKeyDown = false;
      } else {
         boolean rDown = client.currentScreen == null && client.getWindow() != null && InputUtil.isKeyPressed(client.getWindow().getHandle(), 82);
         if (rDown && !wasRKeyDown) {
            localToggled = !localToggled;
            ClientPlayNetworking.send(new TranslucentTogglePayload(localToggled));
         }

         wasRKeyDown = rDown;
      }
   }
}
