package daot;

import daot.network.ButcherTentaclePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;

@Environment(EnvType.CLIENT)
public final class ButcherTentacleInputHandler {
   private static boolean wasRDown = false;

   private ButcherTentacleInputHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(ButcherTentacleInputHandler::tick);
   }

   private static void tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player == null) {
         wasRDown = false;
      } else if (BloodlineClientData.get(player.getUuid()) != BloodlineType.BUTCHER) {
         wasRDown = false;
      } else if (ModEffects.isPowerDisabled(player)) {
         wasRDown = false;
      } else if (mc.currentScreen == null && mc.getWindow() != null) {
         boolean rDown = InputUtil.isKeyPressed(mc.getWindow().getHandle(), 82);
         if (rDown && !wasRDown) {
            ClientPlayNetworking.send(new ButcherTentaclePayload());
         }

         wasRDown = rDown;
      } else {
         wasRDown = false;
      }
   }
}
