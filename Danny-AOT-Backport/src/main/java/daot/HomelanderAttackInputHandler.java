package daot;

import daot.network.HomelanderAttackImpactPayload;
import daot.network.HomelanderAttackPayload;
import daot.network.HomelanderGrabIntentPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class HomelanderAttackInputHandler {
   private static final int ATTACK_COOLDOWN_TICKS = 6;
   private static final int ATTACK_IMPACT_DELAY_TICKS = 6;
   private static boolean lastGrabIntent = false;
   private static boolean wasLmbDown = false;
   private static int attackCooldown = 0;
   private static boolean lastAttackWasOne = false;
   private static int impactTimer = 0;
   private static byte impactType = 0;

   private HomelanderAttackInputHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(HomelanderAttackInputHandler::tick);
   }

   private static void tick(MinecraftClient mc) {
      if (attackCooldown > 0) {
         attackCooldown--;
      }

      ClientPlayerEntity player = mc.player;
      if (player == null) {
         resetState();
      } else {
         if (impactTimer > 0) {
            impactTimer--;
            if (impactTimer == 0 && impactType != 0) {
               ClientPlayNetworking.send(new HomelanderAttackImpactPayload(impactType));
               impactType = 0;
            }
         }

         boolean flying = HomelanderFlightHandler.isFlying(player.getUuid());
         if (!flying) {
            if (lastGrabIntent) {
               lastGrabIntent = false;
               ClientPlayNetworking.send(new HomelanderGrabIntentPayload(false));
               HomelanderPlayerAnimationHandler.setLocalGrabIntent(false);
            }

            wasLmbDown = false;
         } else if (HomelanderPlayerAnimationHandler.isInFlyStart(player.getUuid())) {
            wasLmbDown = false;
         } else {
            boolean rmbHeld = mc.currentScreen == null && mc.options.useKey.isPressed();
            if (rmbHeld != lastGrabIntent) {
               lastGrabIntent = rmbHeld;
               ClientPlayNetworking.send(new HomelanderGrabIntentPayload(rmbHeld));
               HomelanderPlayerAnimationHandler.setLocalGrabIntent(rmbHeld);
            }

            boolean lmbDown = mc.currentScreen == null && mc.options.attackKey.isPressed();
            boolean lmbPressed = lmbDown && !wasLmbDown;
            wasLmbDown = lmbDown;
            if (lmbPressed) {
               if (attackCooldown <= 0) {
                  boolean superFlying = mc.options.sprintKey.isPressed();
                  byte attackType;
                  if (rmbHeld) {
                     attackType = 0;
                  } else {
                     if (superFlying) {
                        return;
                     }

                     attackType = (byte)(lastAttackWasOne ? 2 : 1);
                     lastAttackWasOne = !lastAttackWasOne;
                  }

                  attackCooldown = 6;
                  ClientPlayNetworking.send(new HomelanderAttackPayload(attackType));
                  HomelanderPlayerAnimationHandler.triggerAttack(player, attackType, 6);
                  if (attackType == 1 || attackType == 2) {
                     impactTimer = 6;
                     impactType = attackType;
                  }

                  float shakeIntensity = attackType == 0 ? 0.6F : 0.85F;
                  CameraShakeHandler.triggerFastShake(shakeIntensity);
               }
            }
         }
      }
   }

   private static void resetState() {
      if (lastGrabIntent) {
         lastGrabIntent = false;
      }

      wasLmbDown = false;
      attackCooldown = 0;
      impactTimer = 0;
      impactType = 0;
   }
}
