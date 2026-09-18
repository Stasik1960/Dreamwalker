package daot;

import daot.network.SoldierboyNeckGrabPayload;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;

@Environment(EnvType.CLIENT)
public final class SoldierboyNeckGrabInputHandler {
   private static final long LOCAL_COOLDOWN_TICKS = 200L;
   private static boolean xHeldLast = false;
   private static boolean lmbHeldLast = false;
   private static boolean grabActiveLocal = false;
   private static long cooldownEndTick = 0L;

   private SoldierboyNeckGrabInputHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(SoldierboyNeckGrabInputHandler::tick);
   }

   public static void setLocalGrabActive(boolean active) {
      if (grabActiveLocal && !active) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null) {
            cooldownEndTick = mc.player.getWorld().getTime() + 200L;
         }
      }

      grabActiveLocal = active;
   }

   private static void tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player == null) {
         xHeldLast = false;
         lmbHeldLast = false;
      } else if (BloodlineClientData.get(player.getUuid()) != BloodlineType.SOLDIERBOY) {
         xHeldLast = false;
         lmbHeldLast = false;
      } else if (mc.currentScreen == null && mc.getWindow() != null) {
         UUID uuid = player.getUuid();
         if (SoldierboyPlayerAnimationHandler.isInChestAbilityState(uuid)) {
            xHeldLast = false;
            lmbHeldLast = false;
         } else {
            boolean xHeld = InputUtil.isKeyPressed(mc.getWindow().getHandle(), 88);
            boolean lmbHeld = mc.options.attackKey.isPressed();
            long now = player.getWorld().getTime();
            if (xHeld && !xHeldLast && (grabActiveLocal || now >= cooldownEndTick)) {
               ClientPlayNetworking.send(new SoldierboyNeckGrabPayload((byte)0));
               SoldierboyPlayerAnimationHandler.triggerNeckPunch(player);
            }

            xHeldLast = xHeld;
            if (grabActiveLocal && lmbHeld && !lmbHeldLast) {
               ClientPlayNetworking.send(new SoldierboyNeckGrabPayload((byte)2));
               SoldierboyPlayerAnimationHandler.triggerNeckPunch(player);
            }

            lmbHeldLast = lmbHeld;
         }
      } else {
         xHeldLast = false;
         lmbHeldLast = false;
      }
   }
}
