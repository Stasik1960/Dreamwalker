package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Hand;

@Environment(EnvType.CLIENT)
public class BoostFovHandler {
   private static float currentFovModifier = 0.0F;
   private static float previousFovModifier = 0.0F;
   private static final float FOV_BOOST_AMOUNT = 20.0F;
   private static final float CHARGE_FOV_AMOUNT = 15.0F;
   private static final float AWAKENED_FOV_AMOUNT = 5.0F;
   private static final float ROYAL_SHOUT_FOV_AMOUNT = 5.0F;
   private static final float THUNDER_SPEAR_SNAP_FOV = 5.0F;
   private static final float THUNDER_SPEAR_FIRE_FOV = 3.0F;
   private static final float APG_AIM_FOV = -15.0F;
   private static final float DASH_CHARGE_FOV_AMOUNT = -22.0F;
   private static final float DASH_FOV_AMOUNT = 30.0F;
   private static final float FOV_CHANGE_SPEED = 0.15F;
   private static int royalShoutFovTicks = 0;
   private static int thunderSpearSnapFovTicks = 0;
   private static int thunderSpearFireFovTicks = 0;
   private static int beastRoarFovTicks = 0;
   private static final int BEAST_ROAR_FOV_KEEPALIVE = 4;
   private static final float BEAST_ROAR_FOV_AMOUNT = 10.0F;
   private static int sonicBoomFovTicks = 0;
   private static final int SONIC_BOOM_FOV_DURATION = 14;
   private static final float SONIC_BOOM_FOV_AMOUNT = 18.0F;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(BoostFovHandler::tick);
   }

   public static void triggerRoyalShoutFov() {
      royalShoutFovTicks = 120;
   }

   public static void triggerCommandFov() {
      royalShoutFovTicks = 20;
   }

   public static void triggerThunderSpearSnapFov() {
      thunderSpearSnapFovTicks = 25;
   }

   public static void triggerThunderSpearFireFov() {
      thunderSpearFireFovTicks = 10;
   }

   public static void triggerArmorPotionFov() {
      royalShoutFovTicks = Math.max(royalShoutFovTicks, 40);
   }

   public static void pingBeastRoarFov() {
      beastRoarFovTicks = 4;
   }

   public static void triggerSonicBoomFov() {
      sonicBoomFovTicks = 14;
   }

   private static void tick(MinecraftClient client) {
      previousFovModifier = currentFovModifier;
      ClientPlayerEntity player = client.player;
      if (player == null) {
         currentFovModifier = smoothLerp(currentFovModifier, 0.0F, 0.15F);
      } else {
         float targetFov = 0.0F;
         Entity vehicle = player.getVehicle();
         boolean ridingSprintingShifter = false;
         if (vehicle instanceof AttackTitanEntity at && at.isSprinting() && !at.isInSneakingPose() && !at.isArmed()) {
            ridingSprintingShifter = true;
         } else if (vehicle instanceof ArmoredTitanEntity ar && ar.isSprinting() && !ar.isInSneakingPose() && !ar.isArmed()) {
            ridingSprintingShifter = true;
         } else if (vehicle instanceof FemaleTitanEntity ft && ft.isSprinting() && !ft.isInSneakingPose() && !ft.isArmed()) {
            ridingSprintingShifter = true;
         } else if (vehicle instanceof BeastTitanEntity bt && bt.isSprinting() && !bt.isInSneakingPose() && !bt.isInRockThrowSequence()) {
            ridingSprintingShifter = true;
         } else if (vehicle instanceof ColossalTitanEntity ct && ct.isMoving()) {
            ridingSprintingShifter = true;
         } else if (vehicle instanceof WarhammerTitanEntity wh && wh.isSprinting() && !wh.isInSneakingPose() && !wh.isArmed() && !wh.isTitanAttacking()) {
            ridingSprintingShifter = true;
         }

         if (ridingSprintingShifter) {
            targetFov = 20.0F;
         } else if (isWearingODMGear(player)) {
            HookPoint leftHook = ODMTickHandler.getLeftHook(player.getUuid());
            HookPoint rightHook = ODMTickHandler.getRightHook(player.getUuid());
            boolean leftHookActive = leftHook != null && leftHook.active && !leftHook.isExtending && !leftHook.isRetracting;
            boolean rightHookActive = rightHook != null && rightHook.active && !rightHook.isExtending && !rightHook.isRetracting;
            boolean anyHookActive = leftHookActive || rightHookActive;
            if (anyHookActive) {
               boolean isBoosting = client.options.jumpKey.isPressed();
               targetFov = isBoosting ? 20.0F : 0.0F;
            }
         }

         if (vehicle instanceof ArmoredTitanEntity arc && arc.isBreaching() && arc.isSprinting() && arc.isMoving()) {
            targetFov += (float)(15.0 * arc.chargeRunProgress());
         }

         if (AwakenedPowerClientData.isActive()) {
            targetFov += 5.0F;
         }

         if (TitanDashClientData.isCharging()) {
            targetFov += -22.0F * TitanDashClientData.chargeProgress();
         } else if (TitanDashClientData.isDashing()) {
            targetFov += 30.0F;
         }

         if (royalShoutFovTicks > 0) {
            royalShoutFovTicks--;
            targetFov += 5.0F;
         }

         if (thunderSpearSnapFovTicks > 0) {
            thunderSpearSnapFovTicks--;
            targetFov += 5.0F;
         }

         if (thunderSpearFireFovTicks > 0) {
            thunderSpearFireFovTicks--;
            targetFov += 3.0F;
         }

         if (beastRoarFovTicks > 0) {
            beastRoarFovTicks--;
            targetFov += 10.0F;
         }

         if (sonicBoomFovTicks > 0) {
            sonicBoomFovTicks--;
            targetFov += 18.0F;
         }

         if (BladeAnimationHandler.isAiming(Hand.MAIN_HAND) || BladeAnimationHandler.isAiming(Hand.OFF_HAND)) {
            targetFov += -15.0F;
         }

         currentFovModifier = smoothLerp(currentFovModifier, targetFov, 0.15F);
      }
   }

   private static boolean isWearingODMGear(ClientPlayerEntity player) {
      return DannysAot.isODMGear(player.getEquippedStack(EquipmentSlot.LEGS).getItem());
   }

   private static float smoothLerp(float current, float target, float speed) {
      return current + (target - current) * speed;
   }

   public static float getFovModifier(float partialTick) {
      return previousFovModifier + (currentFovModifier - previousFovModifier) * partialTick;
   }

   public static float getFovModifier() {
      return currentFovModifier;
   }
}
