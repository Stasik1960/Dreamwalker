package daot;

import daot.network.AtrainPlaySpeedSoundPayload;
import daot.network.AtrainSpeedTogglePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;

@Environment(EnvType.CLIENT)
public final class AtrainSpeedHandler {
   private static final long SOUND_COOLDOWN_TICKS = 40L;
   private static boolean wasRKeyDown = false;
   private static boolean wasSprinting = false;
   private static long lastSoundTick = -1L;

   private AtrainSpeedHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(AtrainSpeedHandler::tick);
   }

   private static void tick(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null) {
         wasRKeyDown = false;
         wasSprinting = false;
      } else {
         boolean isAtrain = BloodlineClientData.get(player.getUuid()) == BloodlineType.ATRAIN;
         if (!isAtrain) {
            if (AtrainSpeedClientState.isActive(player.getUuid())) {
               AtrainSpeedClientState.set(player.getUuid(), false);
            }

            wasRKeyDown = false;
            wasSprinting = false;
         } else {
            boolean rDown = client.currentScreen == null && client.getWindow() != null && InputUtil.isKeyPressed(client.getWindow().getHandle(), 82);
            if (rDown && !wasRKeyDown) {
               boolean newState = !AtrainSpeedClientState.isActive(player.getUuid());
               AtrainSpeedClientState.set(player.getUuid(), newState);
               ClientPlayNetworking.send(new AtrainSpeedTogglePayload(newState));
            }

            wasRKeyDown = rDown;
            boolean sprinting = player.isSprinting();
            long now = player.getWorld().getTime();
            if (sprinting && !wasSprinting && AtrainSpeedClientState.isActive(player.getUuid()) && (lastSoundTick < 0L || now - lastSoundTick >= 40L)) {
               lastSoundTick = now;
               ClientPlayNetworking.send(new AtrainPlaySpeedSoundPayload());
            }

            wasSprinting = sprinting;
         }
      }
   }
}
