package daot.mixin.client;

import daot.BeastTitanEntity;
import daot.CameraPanState;
import daot.ShifterHeadCameraAnchor;
import daot.ShifterTitan;
import daot.TitanShiftHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
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
public abstract class ShifterThirdPersonHeadCameraMixin {
   @Unique
   private static final double HEAD_UP_OFFSET = 1.0;
   @Unique
   private static final float BACK_DISTANCE_FACTOR = 0.8F;
   @Unique
   private static final double SNAP_THRESHOLD = 0.3;
   @Unique
   private static final double SNAP_EASE = 0.2;
   @Unique
   private static final double MAX_OFFSET_STEP = 0.1;
   @Unique
   private static final double BASE_HEIGHT = 8.0;
   @Unique
   private static final double MAX_SIZE_SCALE = 2.5;
   @Unique
   private static final float BACK_EASE = 0.12F;
   @Unique
   private static final float TAKEOVER_EASE = 0.035F;
   @Unique
   private static boolean daot$takeoverGlide = false;
   @Unique
   private static float daot$takeoverBlend = 1.0F;
   @Unique
   private static Vec3d daot$smoothedOffset = null;
   @Unique
   private static int daot$smoothedEntityId = -1;
   @Unique
   private static float daot$smoothedBack = -1.0F;
   @Unique
   private static float daot$stableHeight = -1.0F;
   @Unique
   private static final float EJECT_DISTANCE = 4.0F;
   @Unique
   private static final float EJECT_EASE_IN = 0.01375F;
   @Unique
   private static final float EJECT_EASE_OUT = 0.0075F;
   @Unique
   private static float daot$ejectBlend = 0.0F;
   @Unique
   private static final float AIM_PAN_TARGET = 6.0F;
   @Unique
   private static float daot$aimPan = 0.0F;
   @Unique
   private static float daot$prevAimPan = 0.0F;

   @Shadow
   protected abstract void setPos(double var1, double var3, double var5);

   @Shadow
   protected abstract void moveBy(double var1, double var2, double var3);

   @Shadow
   protected abstract double clipToSpace(double var1);

   @Inject(method = "update", at = @At("TAIL"))
   private void anchorToShifterHead(BlockView level, Entity focusedEntity, boolean thirdPerson, boolean mirrored, float partialTick, CallbackInfo ci) {
      if (thirdPerson) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null) {
            Entity vehicle = mc.player.getVehicle();
            if (vehicle instanceof ShifterTitan shifter) {
               if (TitanShiftHandler.isShiftZoomActive()) {
                  daot$smoothedEntityId = -1;
               } else {
                  Vec3d target = ShifterHeadCameraAnchor.get(vehicle.getId());
                  if (target != null) {
                     boolean reset = vehicle.getId() != daot$smoothedEntityId || daot$smoothedOffset == null;
                     if (reset) {
                        daot$smoothedOffset = target;
                        daot$stableHeight = vehicle.getHeight();
                        daot$ejectBlend = shifter.isDismounting() ? 1.0F : 0.0F;
                        daot$smoothedEntityId = vehicle.getId();
                     } else {
                        daot$stableHeight = Math.max(daot$stableHeight, vehicle.getHeight());
                        double sizeScale = Math.max(1.0, Math.min(2.5, daot$stableHeight / 8.0));
                        double snapThreshold = 0.3 * sizeScale;
                        double maxStep = 0.1 * sizeScale;
                        Vec3d delta = target.subtract(daot$smoothedOffset);
                        double dist = delta.length();
                        if (dist > 1.0E-6) {
                           double step;
                           if (dist <= snapThreshold) {
                              step = Math.min(dist, maxStep);
                           } else {
                              step = Math.min(dist * 0.2, maxStep);
                           }

                           daot$smoothedOffset = daot$smoothedOffset.add(delta.multiply(step / dist));
                        }

                        float ejectTarget = shifter.isDismounting() ? 1.0F : 0.0F;
                        float ejectEase = ejectTarget > daot$ejectBlend ? 0.01375F : 0.0075F;
                        daot$ejectBlend = daot$ejectBlend + (ejectTarget - daot$ejectBlend) * ejectEase;
                     }

                     Vec3d headOffset = daot$smoothedOffset;
                     Vec3d entityPos = vehicle.getLerpedPos(partialTick);
                     double pilotX = entityPos.x + headOffset.x;
                     double pilotY = entityPos.y + headOffset.y + 1.0;
                     double pilotZ = entityPos.z + headOffset.z;
                     float pilotDesired = daot$stableHeight * 0.8F;
                     Vec3d eye = mc.player.getCameraPosVec(partialTick);
                     float b = daot$ejectBlend;
                     double anchorX = pilotX + (eye.x - pilotX) * b;
                     double anchorY = pilotY + (eye.y - pilotY) * b;
                     double anchorZ = pilotZ + (eye.z - pilotZ) * b;
                     float desired = pilotDesired + (4.0F - pilotDesired) * b;
                     if (daot$takeoverBlend < 1.0F) {
                        daot$takeoverBlend = daot$takeoverBlend + (1.0F - daot$takeoverBlend) * 0.035F;
                        if (daot$takeoverBlend > 0.995F) {
                           daot$takeoverBlend = 1.0F;
                        }

                        float tb = daot$takeoverBlend;
                        anchorX = eye.x + (anchorX - eye.x) * tb;
                        anchorY = eye.y + (anchorY - eye.y) * tb;
                        anchorZ = eye.z + (anchorZ - eye.z) * tb;
                     }

                     this.setPos(anchorX, anchorY, anchorZ);
                     float clampedBack = (float)this.clipToSpace(desired);
                     if (!reset && !(daot$smoothedBack < 0.0F)) {
                        daot$smoothedBack = daot$smoothedBack + (clampedBack - daot$smoothedBack) * (daot$takeoverGlide ? 0.035F : 0.12F);
                        if (daot$takeoverGlide && Math.abs(clampedBack - daot$smoothedBack) < 0.25F) {
                           daot$takeoverGlide = false;
                        }
                     } else {
                        float zoom = TitanShiftHandler.getCurrentZoomMultiplier();
                        if (zoom > 1.25F) {
                           daot$smoothedBack = (float)this.clipToSpace(4.0F * zoom);
                           daot$takeoverGlide = true;
                           daot$takeoverBlend = 0.0F;
                        } else {
                           daot$smoothedBack = clampedBack;
                           daot$takeoverGlide = false;
                           daot$takeoverBlend = 1.0F;
                        }
                     }

                     this.moveBy(-daot$smoothedBack, 0.0F, 0.0F);
                     float panTarget = 0.0F;
                     if (vehicle instanceof BeastTitanEntity beast) {
                        int phase = beast.getRockThrowPhase();
                        if ((phase == 2 || phase == 3) && beast.isThrowTurning()) {
                           panTarget = 6.0F;
                        }
                     }

                     daot$prevAimPan = daot$aimPan;
                     float panRemaining = Math.abs(panTarget - daot$aimPan);
                     float panT = Math.min(panRemaining / 6.0F, 1.0F);
                     float panEased = 1.0F - (1.0F - panT) * (1.0F - panT) * (1.0F - panT);
                     float panLerp = 0.008F + 0.032F * panEased;
                     daot$aimPan = daot$aimPan + (panTarget - daot$aimPan) * panLerp;
                     if (Math.abs(daot$aimPan - panTarget) < 0.003F) {
                        daot$aimPan = panTarget;
                     }

                     float interpAimPan = daot$prevAimPan + (daot$aimPan - daot$prevAimPan) * partialTick;
                     if (Math.abs(interpAimPan) > 0.01F) {
                        this.moveBy(0.0F, 0.0F, interpAimPan);
                     }

                     CameraPanState.horizontalPan = interpAimPan;
                  }
               }
            }
         }
      }
   }
}
