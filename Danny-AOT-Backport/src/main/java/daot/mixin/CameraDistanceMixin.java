package daot.mixin;

import daot.BeastTitanEntity;
import daot.CameraPanState;
import daot.CombatModeState;
import daot.DannysAot;
import daot.HomelanderFlightHandler;
import daot.HomelanderPlayerAnimationHandler;
import daot.HookPoint;
import daot.ODMTickHandler;
import daot.ShifterHeadCameraAnchor;
import daot.ShifterTitan;
import daot.TitanShiftHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CameraDistanceMixin {
   @Shadow
   private Entity focusedEntity;
   @Unique
   private static float currentHorizontalPan = 0.0F;
   @Unique
   private static float prevHorizontalPan = 0.0F;
   @Unique
   private static float currentVerticalPan = 0.0F;
   @Unique
   private static float prevVerticalPan = 0.0F;
   @Unique
   private static final float AIM_PAN_TARGET = 6.0F;
   @Unique
   private static final float ODM_PAN_HORIZONTAL = 1.2F;
   @Unique
   private static final float ODM_PAN_VERTICAL = 0.6F;
   @Unique
   private static final float HOMELANDER_FLIGHT_PAN = 0.6F;
   @Unique
   private static float lockedH = 0.0F;
   @Unique
   private static float lockedV = 0.0F;
   @Unique
   private static boolean panLocked = false;
   @Unique
   private static float currentDragH = 0.0F;
   @Unique
   private static float prevDragH = 0.0F;
   @Unique
   private static float currentDragV = 0.0F;
   @Unique
   private static float prevDragV = 0.0F;
   @Unique
   private static final float DRAG_FACTOR = 0.6F;
   @Unique
   private static final float DRAG_SMOOTH = 0.06F;
   @Unique
   private static float currentShiftLockPan = 0.0F;
   @Unique
   private static float prevShiftLockPan = 0.0F;

   @Shadow
   protected abstract void moveBy(double var1, double var2, double var3);

   @Shadow
   protected abstract double clipToSpace(double var1);

   @Inject(method = "update", at = @At("TAIL"))
   private void extendCameraDistance(BlockView level, Entity focusedEntity, boolean thirdPerson, boolean mirrored, float partialTick, CallbackInfo ci) {
      if (thirdPerson) {
         Entity daot$vehicle = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getVehicle() : null;
         if (!(daot$vehicle instanceof ShifterTitan) || ShifterHeadCameraAnchor.get(daot$vehicle.getId()) == null || TitanShiftHandler.isShiftZoomActive()) {
            float zoomMultiplier = TitanShiftHandler.getInterpolatedZoom(partialTick);
            if (zoomMultiplier > 1.01F) {
               float additionalDistance = 4.0F * (zoomMultiplier - 1.0F);
               float maxAdditional = (float)this.clipToSpace(additionalDistance);
               this.moveBy(-maxAdditional, 0.0F, 0.0F);
            }

            if (CombatModeState.isEnabled()) {
               float combatZoom = CombatModeState.getInterpolatedZoom(partialTick);
               float additional = combatZoom - 4.0F;
               if (additional > 0.01F) {
                  float maxAdditional = (float)this.clipToSpace(additional);
                  this.moveBy(-maxAdditional, 0.0F, 0.0F);
               }
            }

            float shiftLockTarget = CombatModeState.isShiftLockEnabled() ? CombatModeState.getShiftLockPan() : 0.0F;
            prevShiftLockPan = currentShiftLockPan;
            currentShiftLockPan = currentShiftLockPan + (shiftLockTarget - currentShiftLockPan) * 0.08F;
            if (Math.abs(currentShiftLockPan - shiftLockTarget) < 0.005F) {
               currentShiftLockPan = shiftLockTarget;
            }

            float interpShiftLock = prevShiftLockPan + (currentShiftLockPan - prevShiftLockPan) * partialTick;
            if (Math.abs(interpShiftLock) > 0.01F) {
               this.moveBy(0.0F, 0.0F, interpShiftLock);
            }

            float crouchOffset = TitanShiftHandler.getInterpolatedCrouchOffset(partialTick);
            if (Math.abs(crouchOffset) > 0.01F) {
               this.moveBy(0.0F, crouchOffset, 0.0F);
            }

            PlayerEntity player = MinecraftClient.getInstance().player;
            float targetH = 0.0F;
            float targetV = 0.0F;
            if (player != null && player.getVehicle() instanceof BeastTitanEntity beast) {
               int phase = beast.getRockThrowPhase();
               if ((phase == 2 || phase == 3) && beast.isThrowTurning()) {
                  targetH = 6.0F;
               }
            }

            if (targetH == 0.0F && player != null && DannysAot.isODMGear(player.getEquippedStack(EquipmentSlot.LEGS).getItem())) {
               MinecraftClient mc = MinecraftClient.getInstance();
               HookPoint leftHook = ODMTickHandler.getLeftHook();
               HookPoint rightHook = ODMTickHandler.getRightHook();
               boolean leftLatched = leftHook != null && leftHook.active && !leftHook.isExtending && !leftHook.isRetracting;
               boolean rightLatched = rightHook != null && rightHook.active && !rightHook.isExtending && !rightHook.isRetracting;
               boolean pressingA = mc.options.leftKey.isPressed();
               boolean pressingD = mc.options.rightKey.isPressed();
               boolean onGround = player.isOnGround();
               boolean hasActiveDirection = false;
               if (leftLatched && rightLatched) {
                  targetV = 0.6F;
                  hasActiveDirection = true;
               } else if (rightLatched) {
                  if (pressingD) {
                     targetH = 1.2F;
                  } else if (pressingA) {
                     targetH = -1.2F;
                  } else {
                     targetV = 0.6F;
                  }

                  hasActiveDirection = true;
               } else if (leftLatched) {
                  if (pressingA) {
                     targetH = -1.2F;
                  } else if (pressingD) {
                     targetH = 1.2F;
                  } else {
                     targetV = 0.6F;
                  }

                  hasActiveDirection = true;
               }

               if (hasActiveDirection) {
                  lockedH = targetH;
                  lockedV = targetV;
                  panLocked = true;
               } else if (!onGround && panLocked) {
                  targetH = lockedH;
                  targetV = lockedV;
               } else {
                  panLocked = false;
                  lockedH = 0.0F;
                  lockedV = 0.0F;
               }
            }

            if (targetH == 0.0F
               && targetV == 0.0F
               && player != null
               && HomelanderFlightHandler.isFlying(player.getUuid())
               && !HomelanderPlayerAnimationHandler.isInFlyStart(player.getUuid())) {
               targetH = 0.6F;
            }

            prevHorizontalPan = currentHorizontalPan;
            float hRemaining = Math.abs(targetH - currentHorizontalPan);
            float hMax = Math.max(6.0F, 1.2F);
            float hT = Math.min(hRemaining / hMax, 1.0F);
            float hEased = 1.0F - (1.0F - hT) * (1.0F - hT) * (1.0F - hT);
            float hLerp = 0.008F + 0.032F * hEased;
            currentHorizontalPan = currentHorizontalPan + (targetH - currentHorizontalPan) * hLerp;
            if (Math.abs(currentHorizontalPan - targetH) < 0.003F) {
               currentHorizontalPan = targetH;
            }

            prevVerticalPan = currentVerticalPan;
            float vRemaining = Math.abs(targetV - currentVerticalPan);
            float vT = Math.min(vRemaining / Math.max(0.6F, 0.01F), 1.0F);
            float vEased = 1.0F - (1.0F - vT) * (1.0F - vT) * (1.0F - vT);
            float vLerp = 0.008F + 0.032F * vEased;
            currentVerticalPan = currentVerticalPan + (targetV - currentVerticalPan) * vLerp;
            if (Math.abs(currentVerticalPan - targetV) < 0.003F) {
               currentVerticalPan = targetV;
            }

            float interpH = prevHorizontalPan + (currentHorizontalPan - prevHorizontalPan) * partialTick;
            float interpV = prevVerticalPan + (currentVerticalPan - prevVerticalPan) * partialTick;
            if (Math.abs(interpH) > 0.01F || Math.abs(interpV) > 0.01F) {
               this.moveBy(0.0F, interpV, interpH);
            }

            if (player != null && DannysAot.isODMGear(player.getEquippedStack(EquipmentSlot.LEGS).getItem())) {
               Vec3d vel = player.getVelocity();
               float yawRad = (float)Math.toRadians(player.getYaw());
               float sinYaw = (float)Math.sin(yawRad);
               float cosYaw = (float)Math.cos(yawRad);
               float sidewaysVel = (float)(vel.x * cosYaw + vel.z * sinYaw);
               float forwardVel = (float)(vel.x * -sinYaw + vel.z * cosYaw);
               float targetDragH = -sidewaysVel * 0.6F;
               float targetDragV = (float)(-vel.y * 0.6F * 0.5);
               prevDragH = currentDragH;
               currentDragH = currentDragH + (targetDragH - currentDragH) * 0.06F;
               if (Math.abs(currentDragH) < 0.002F) {
                  currentDragH = 0.0F;
               }

               prevDragV = currentDragV;
               currentDragV = currentDragV + (targetDragV - currentDragV) * 0.06F;
               if (Math.abs(currentDragV) < 0.002F) {
                  currentDragV = 0.0F;
               }

               float dragH = prevDragH + (currentDragH - prevDragH) * partialTick;
               float dragV = prevDragV + (currentDragV - prevDragV) * partialTick;
               float backDrag = -Math.abs(forwardVel) * 0.6F * 0.3F;
               if (Math.abs(dragH) > 0.005F || Math.abs(dragV) > 0.005F || Math.abs(backDrag) > 0.005F) {
                  this.moveBy(backDrag, dragV, dragH);
               }
            } else {
               currentDragH = 0.0F;
               prevDragH = 0.0F;
               currentDragV = 0.0F;
               prevDragV = 0.0F;
            }

            CameraPanState.horizontalPan = interpH;
            CameraPanState.verticalPan = interpV;
         }
      }
   }
}
