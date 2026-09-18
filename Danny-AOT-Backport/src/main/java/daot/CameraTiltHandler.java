package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class CameraTiltHandler {
   private static float currentTilt = 0.0F;
   private static float previousTilt = 0.0F;
   private static float currentPitch = 0.0F;
   private static float previousPitch = 0.0F;
   private static boolean hasLandedSinceHook = false;
   private static final float MAX_TILT = 35.0F;
   private static final float MAX_PITCH = 0.0F;
   private static final float TILT_SPEED = 0.2F;
   private static final float PITCH_SPEED = 0.15F;
   private static final float SLOW_TILT_SPEED = 0.05F;
   private static final float SLOW_PITCH_SPEED = 0.04F;
   private static final double MIN_SPEED_FOR_TILT = 0.1;
   private static final double MAX_SPEED_FOR_TILT = 1.5;
   private static final double MIN_SPEED_FOR_PITCH = 0.3;
   private static final double MAX_SPEED_FOR_PITCH = 2.0;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(CameraTiltHandler::tick);
   }

   private static void tick(MinecraftClient client) {
      previousTilt = currentTilt;
      previousPitch = currentPitch;
      ClientPlayerEntity player = client.player;
      if (player == null) {
         currentTilt = smoothLerp(currentTilt, 0.0F, 0.2F);
         currentPitch = smoothLerp(currentPitch, 0.0F, 0.15F);
      } else if (!isWearingODMGear(player)) {
         currentTilt = smoothLerp(currentTilt, 0.0F, 0.2F);
         currentPitch = smoothLerp(currentPitch, 0.0F, 0.15F);
      } else {
         HookPoint leftHook = ODMTickHandler.getLeftHook(player.getUuid());
         HookPoint rightHook = ODMTickHandler.getRightHook(player.getUuid());
         boolean leftHookActive = leftHook != null && leftHook.active && !leftHook.isExtending && !leftHook.isRetracting;
         boolean rightHookActive = rightHook != null && rightHook.active && !rightHook.isExtending && !rightHook.isRetracting;
         boolean bothHooksActive = leftHookActive && rightHookActive;
         boolean anyHookActive = leftHookActive || rightHookActive;
         Vec3d velocity = player.getVelocity();
         double horizontalSpeed = velocity.horizontalLength();
         if (!anyHookActive) {
            if (player.isOnGround()) {
               hasLandedSinceHook = true;
            }

            float tiltSpeed = hasLandedSinceHook ? 0.2F : 0.05F;
            float pitchSpeed = hasLandedSinceHook ? 0.15F : 0.04F;
            currentTilt = smoothLerp(currentTilt, 0.0F, tiltSpeed);
            currentPitch = smoothLerp(currentPitch, 0.0F, pitchSpeed);
         } else {
            hasLandedSinceHook = false;
            boolean isBoosting = client.options.jumpKey.isPressed();
            if (isBoosting) {
               double pitchSpeedFactor = (horizontalSpeed - 0.3) / 1.7;
               pitchSpeedFactor = Math.max(0.0, Math.min(1.0, pitchSpeedFactor));
               float targetPitch = (float)(0.0 * pitchSpeedFactor);
               currentPitch = smoothLerp(currentPitch, targetPitch, 0.15F);
            } else {
               currentPitch = smoothLerp(currentPitch, 0.0F, 0.15F);
            }

            if (bothHooksActive) {
               currentTilt = smoothLerp(currentTilt, 0.0F, 0.2F);
            } else {
               double speedFactor = (horizontalSpeed - 0.1) / 1.4;
               speedFactor = Math.max(0.0, Math.min(1.0, speedFactor));
               float speedBasedMaxTilt = (float)(35.0 * speedFactor);
               float targetTilt = 0.0F;
               if (client.options.leftKey.isPressed()) {
                  targetTilt = -speedBasedMaxTilt;
               } else if (client.options.rightKey.isPressed()) {
                  targetTilt = speedBasedMaxTilt;
               }

               currentTilt = smoothLerp(currentTilt, targetTilt, 0.2F);
            }
         }
      }
   }

   private static boolean isWearingODMGear(ClientPlayerEntity player) {
      return DannysAot.isODMGear(player.getEquippedStack(EquipmentSlot.LEGS).getItem());
   }

   private static float smoothLerp(float current, float target, float speed) {
      return current + (target - current) * speed;
   }

   public static float getCurrentTilt(float partialTick) {
      return previousTilt + (currentTilt - previousTilt) * partialTick;
   }

   public static float getCurrentTilt() {
      return currentTilt;
   }

   public static float getCurrentPitch(float partialTick) {
      return previousPitch + (currentPitch - previousPitch) * partialTick;
   }

   public static float getCurrentPitch() {
      return currentPitch;
   }
}
