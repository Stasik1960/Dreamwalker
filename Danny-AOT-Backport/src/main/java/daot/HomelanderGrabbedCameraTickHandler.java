package daot;

import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class HomelanderGrabbedCameraTickHandler {
   private HomelanderGrabbedCameraTickHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(HomelanderGrabbedCameraTickHandler::tick);
   }

   private static void tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null) {
         if (mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            UUID grabberUuid = HomelanderGrabClientHandler.findGrabberOf(player.getId());
            if (grabberUuid != null) {
               if (HomelanderGrabClientHandler.hasGrabberRotation()) {
                  float aimYaw = MathHelper.wrapDegrees(HomelanderGrabClientHandler.getGrabberYawInterpolated() + 180.0F);
                  float aimPitch = MathHelper.clamp(-HomelanderGrabClientHandler.getGrabberPitchInterpolated(), -90.0F, 90.0F);
                  player.setYaw(aimYaw);
                  player.setPitch(aimPitch);
                  player.setHeadYaw(aimYaw);
               }
            }
         }
      }
   }
}
