package daot;

import daot.network.HomelanderLaserPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class HomelanderLaserInputHandler {
   private static boolean gHeldLast = false;

   private HomelanderLaserInputHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(HomelanderLaserInputHandler::tick);
   }

   private static void tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player == null) {
         gHeldLast = false;
      } else if (BloodlineClientData.get(player.getUuid()) != BloodlineType.HOMELANDER) {
         gHeldLast = false;
      } else if (ModEffects.isPowerDisabled(player)) {
         if (gHeldLast) {
            HomelanderLaserClientHandler.forceStopLocal(player.getUuid());
         }

         gHeldLast = false;
      } else if (mc.currentScreen == null && mc.getWindow() != null && !HomelanderPlayerAnimationHandler.isInFlyStart(player.getUuid())) {
         boolean gHeld = InputUtil.isKeyPressed(mc.getWindow().getHandle(), 71);
         if (gHeld && !gHeldLast) {
            HomelanderLaserClientHandler.setActive(player.getUuid(), true);
         }

         if (gHeld) {
            Vec3d look;
            if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
               look = player.getRotationVec(1.0F);
            } else {
               Quaternionf camRot = new Quaternionf(mc.gameRenderer.getCamera().getRotation());
               Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
               camRot.transform(forward);
               look = new Vec3d(forward.x, forward.y, forward.z);
            }

            if (look.lengthSquared() >= 1.0E-4) {
               look = look.normalize();
               ClientPlayNetworking.send(new HomelanderLaserPayload(look.x, look.y, look.z));
            }
         } else if (gHeldLast) {
            HomelanderLaserClientHandler.forceStopLocal(player.getUuid());
         }

         gHeldLast = gHeld;
      } else {
         if (gHeldLast) {
            HomelanderLaserClientHandler.forceStopLocal(player.getUuid());
         }

         gHeldLast = false;
      }
   }
}
