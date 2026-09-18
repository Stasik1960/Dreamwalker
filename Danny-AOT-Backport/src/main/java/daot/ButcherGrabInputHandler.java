package daot;

import daot.network.ButcherGrabHoldPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;

@Environment(EnvType.CLIENT)
public final class ButcherGrabInputHandler {
   private static boolean wasGDown = false;

   private ButcherGrabInputHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(ButcherGrabInputHandler::tick);
   }

   private static void tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player == null) {
         wasGDown = false;
      } else if (BloodlineClientData.get(player.getUuid()) != BloodlineType.BUTCHER) {
         if (wasGDown) {
            ClientPlayNetworking.send(new ButcherGrabHoldPayload(false));
            wasGDown = false;
         }
      } else if (ModEffects.isPowerDisabled(player)) {
         if (wasGDown) {
            ClientPlayNetworking.send(new ButcherGrabHoldPayload(false));
            wasGDown = false;
         }
      } else if (mc.currentScreen == null && mc.getWindow() != null) {
         boolean gDown = InputUtil.isKeyPressed(mc.getWindow().getHandle(), 71);
         if (gDown && !wasGDown) {
            ClientPlayNetworking.send(new ButcherGrabHoldPayload(true));
         } else if (!gDown && wasGDown) {
            ClientPlayNetworking.send(new ButcherGrabHoldPayload(false));
         }

         wasGDown = gDown;
      } else {
         if (wasGDown) {
            ClientPlayNetworking.send(new ButcherGrabHoldPayload(false));
            wasGDown = false;
         }
      }
   }
}
