package daot.mixin;

import daot.APGGunItem;
import daot.BladeAnimationHandler;
import daot.BladeArmRenderer;
import daot.DannysAot;
import daot.FlareGunItem;
import daot.HookPoint;
import daot.ODMTickHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemRenderer.class)
public class ItemInHandRendererMixin {
   @Unique
   private boolean dannysaot_pushedPose = false;
   @Unique
   private AbstractClientPlayerEntity dannysaot_currentPlayer;
   @Unique
   private Hand dannysaot_currentHand;
   @Unique
   private VertexConsumerProvider dannysaot_bufferSource;
   @Unique
   private int dannysaot_combinedLight;
   @Unique
   private float dannysaot_partialTick;
   @Unique
   private boolean dannysaot_isSwinging = false;
   @Unique
   private float dannysaot_totalPitch = 0.0F;
   @Unique
   private float dannysaot_totalRoll = 0.0F;
   @Unique
   private float dannysaot_totalYaw = 0.0F;
   @Unique
   private static float dannysaot_apgSmoothedPitchMain = 0.0F;
   @Unique
   private static float dannysaot_apgSmoothedYawMain = 0.0F;
   @Unique
   private static float dannysaot_apgSmoothedRollMain = 0.0F;
   @Unique
   private static float dannysaot_apgSmoothedPitchOff = 0.0F;
   @Unique
   private static float dannysaot_apgSmoothedYawOff = 0.0F;
   @Unique
   private static float dannysaot_apgSmoothedRollOff = 0.0F;
   @Unique
   private static final float DANNYSAOT_APG_SMOOTH_RATE = 0.25F;

   @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
   private void applyBladeAnimationHead(
      AbstractClientPlayerEntity player,
      float partialTick,
      float pitch,
      Hand hand,
      float swingProgress,
      ItemStack itemStack,
      float equipProgress,
      MatrixStack poseStack,
      VertexConsumerProvider bufferSource,
      int combinedLight,
      CallbackInfo ci
   ) {
      this.dannysaot_pushedPose = false;
      this.dannysaot_isSwinging = false;
      this.dannysaot_totalPitch = 0.0F;
      this.dannysaot_totalRoll = 0.0F;
      this.dannysaot_totalYaw = 0.0F;
      boolean isBlade = itemStack.getItem() == DannysAot.BLADE;
      boolean isApgGun = itemStack.getItem() == DannysAot.APG_GUN;
      if (!isApgGun) {
         if (hand == Hand.MAIN_HAND) {
            BladeArmRenderer.apgFirstPersonHandOffsetMain = null;
         } else {
            BladeArmRenderer.apgFirstPersonHandOffsetOff = null;
         }
      }

      if (isBlade || isApgGun) {
         this.dannysaot_currentPlayer = player;
         this.dannysaot_currentHand = hand;
         this.dannysaot_bufferSource = bufferSource;
         this.dannysaot_combinedLight = combinedLight;
         this.dannysaot_partialTick = partialTick;
         boolean mainIsRight = player.getMainArm() == Arm.RIGHT;
         boolean isRightSide = hand == Hand.MAIN_HAND ? mainIsRight : !mainIsRight;
         float sideMultiplier = isRightSide ? 1.0F : -1.0F;
         boolean needsPose = false;
         float hookAnim = BladeAnimationHandler.getAnimation(hand, partialTick);
         if (hookAnim > 0.001F) {
            float easedAnimation = this.easeOutCubic(hookAnim);
            if (BladeAnimationHandler.isFiring(hand)) {
               this.dannysaot_totalPitch += easedAnimation * 12.0F;
               if (!isApgGun) {
                  needsPose = true;
               }
            } else {
               float boostAmount = BladeAnimationHandler.getBoostAnimation(partialTick);
               float rollAmount = 15.0F + 15.0F * boostAmount;
               this.dannysaot_totalRoll += easedAnimation * rollAmount * sideMultiplier;
               float[] dynamic = isApgGun ? this.computeApgLatchedAim(player, hand, isRightSide, partialTick) : null;
               if (dynamic != null) {
                  this.dannysaot_totalPitch = this.dannysaot_totalPitch + easedAnimation * dynamic[0];
                  this.dannysaot_totalYaw = this.dannysaot_totalYaw + easedAnimation * dynamic[1];
               } else {
                  this.dannysaot_totalPitch += easedAnimation * 22.5F;
                  this.dannysaot_totalYaw += easedAnimation * 15.0F * sideMultiplier;
               }

               if (!isApgGun) {
                  needsPose = true;
               }
            }
         }

         if (isApgGun) {
            float aimAnim = BladeAnimationHandler.getAimAnimation(hand, partialTick);
            if (aimAnim > 0.001F) {
               float easedAim = this.easeOutCubic(aimAnim);
               this.dannysaot_totalPitch += easedAim * 14.0F;
               this.dannysaot_totalYaw += easedAim * 12.0F * sideMultiplier;
               this.dannysaot_totalRoll += easedAim * -4.0F * sideMultiplier;
            }

            float recoil = BladeAnimationHandler.getApgFireRecoil(hand, partialTick);
            if (recoil > 0.001F) {
               float easedRecoil = this.easeOutCubic(recoil);
               this.dannysaot_totalPitch += easedRecoil * 55.0F;
            }
         }

         if (isBlade) {
            float thunderAnim = BladeAnimationHandler.getThunderAnimation(hand, partialTick);
            if (Math.abs(thunderAnim) > 0.001F) {
               needsPose = true;
               float sign = Math.signum(thunderAnim);
               float easedThunder = this.easeOutCubic(Math.abs(thunderAnim));
               this.dannysaot_totalPitch += sign * easedThunder * 20.0F;
               this.dannysaot_totalRoll += sign * easedThunder * 8.0F * sideMultiplier;
            }

            float swingAnim = BladeAnimationHandler.getSwingAnimation(hand, partialTick);
            if (swingAnim > 0.001F) {
               needsPose = true;
               this.dannysaot_isSwinging = true;
               float[] rotations = this.calculateSwingRotations(swingAnim, sideMultiplier);
               this.dannysaot_totalPitch = this.dannysaot_totalPitch + rotations[0];
               this.dannysaot_totalRoll = this.dannysaot_totalRoll + rotations[1];
               this.dannysaot_totalYaw = this.dannysaot_totalYaw + rotations[2];
            }

            float blockAnim = BladeAnimationHandler.getBlockAnimation(partialTick);
            if (blockAnim > 0.001F) {
               needsPose = true;
               float easedBlock = this.easeOutCubic(blockAnim);
               this.dannysaot_totalRoll += easedBlock * 60.0F * sideMultiplier;
               this.dannysaot_totalPitch += easedBlock * -10.0F;
               this.dannysaot_totalYaw += easedBlock * 15.0F * sideMultiplier;
            }
         }

         if (isApgGun) {
            boolean mainSlot = hand == Hand.MAIN_HAND;
            float sPitch = mainSlot ? dannysaot_apgSmoothedPitchMain : dannysaot_apgSmoothedPitchOff;
            float sYaw = mainSlot ? dannysaot_apgSmoothedYawMain : dannysaot_apgSmoothedYawOff;
            float sRoll = mainSlot ? dannysaot_apgSmoothedRollMain : dannysaot_apgSmoothedRollOff;
            sPitch += (this.dannysaot_totalPitch - sPitch) * 0.25F;
            sYaw += (this.dannysaot_totalYaw - sYaw) * 0.25F;
            sRoll += (this.dannysaot_totalRoll - sRoll) * 0.25F;
            if (mainSlot) {
               dannysaot_apgSmoothedPitchMain = sPitch;
               dannysaot_apgSmoothedYawMain = sYaw;
               dannysaot_apgSmoothedRollMain = sRoll;
            } else {
               dannysaot_apgSmoothedPitchOff = sPitch;
               dannysaot_apgSmoothedYawOff = sYaw;
               dannysaot_apgSmoothedRollOff = sRoll;
            }

            this.dannysaot_totalPitch = sPitch;
            this.dannysaot_totalYaw = sYaw;
            this.dannysaot_totalRoll = sRoll;
            needsPose = Math.abs(sPitch) + Math.abs(sYaw) + Math.abs(sRoll) > 0.05F;
         }

         if (needsPose) {
            poseStack.push();
            this.dannysaot_pushedPose = true;
            poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.dannysaot_totalPitch));
            poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(this.dannysaot_totalRoll));
            poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.dannysaot_totalYaw));
         }

         if (isApgGun) {
            float side = isRightSide ? 1.0F : -1.0F;
            Vector3f p = new Vector3f(side * 1.1F, -0.72F, -0.85F);
            p.rotateY((float)Math.toRadians(this.dannysaot_totalYaw));
            p.rotateZ((float)Math.toRadians(this.dannysaot_totalRoll));
            p.rotateX((float)Math.toRadians(this.dannysaot_totalPitch));
            Vec3d localOffset = new Vec3d(p.x, p.y, p.z);
            if (hand == Hand.MAIN_HAND) {
               BladeArmRenderer.apgFirstPersonHandOffsetMain = localOffset;
            } else {
               BladeArmRenderer.apgFirstPersonHandOffsetOff = localOffset;
            }
         }
      }
   }

   @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
   private void applyBladeAnimationReturn(
      AbstractClientPlayerEntity player,
      float partialTick,
      float pitch,
      Hand hand,
      float swingProgress,
      ItemStack itemStack,
      float equipProgress,
      MatrixStack poseStack,
      VertexConsumerProvider bufferSource,
      int combinedLight,
      CallbackInfo ci
   ) {
      if (itemStack.getItem() == DannysAot.BLADE || itemStack.getItem() == DannysAot.APG_GUN) {
         float hookAnim = BladeAnimationHandler.getAnimation(hand, partialTick);
         float swingAnim = BladeAnimationHandler.getSwingAnimation(hand, partialTick);
         float animation = Math.max(hookAnim, swingAnim);
         BladeArmRenderer.renderArm(
            poseStack,
            bufferSource,
            player,
            hand,
            combinedLight,
            animation,
            partialTick,
            this.dannysaot_totalPitch,
            this.dannysaot_totalRoll,
            this.dannysaot_totalYaw
         );
      }

      if (this.dannysaot_pushedPose) {
         poseStack.pop();
         this.dannysaot_pushedPose = false;
      }
   }

   @Redirect(
      method = "updateHeldItems",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;areEqual(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z")
   )
   private static boolean dannysaot_suppressFlareGunBob(ItemStack stack1, ItemStack stack2) {
      if (stack1.getItem() instanceof FlareGunItem && stack2.getItem() instanceof FlareGunItem) {
         return true;
      } else {
         return stack1.getItem() instanceof APGGunItem && stack2.getItem() instanceof APGGunItem ? true : ItemStack.areEqual(stack1, stack2);
      }
   }

   @Unique
   private float[] calculateSwingRotations(float swingAnim, float sideMultiplier) {
      float pitchRotation;
      float rollRotation;
      float yawRotation;
      if (swingAnim < 0.3F) {
         float phase = swingAnim / 0.3F;
         float eased = this.easeWithAnticipation(phase);
         pitchRotation = eased * -22.0F;
         rollRotation = eased * 0.0F * sideMultiplier;
         yawRotation = eased * 0.0F * sideMultiplier;
      } else if (swingAnim < 0.65F) {
         float phase = (swingAnim - 0.3F) / 0.35F;
         float eased = this.easeOutCubic(phase);
         pitchRotation = -22.0F + 50.0F * eased;
         rollRotation = (0.0F + 25.0F * eased) * sideMultiplier;
         yawRotation = (0.0F + -8.0F * eased) * sideMultiplier;
      } else {
         float phase = (swingAnim - 0.65F) / 0.35F;
         float eased = this.easeOutCubic(phase);
         pitchRotation = 28.0F * (1.0F - eased);
         rollRotation = 25.0F * (1.0F - eased) * sideMultiplier;
         yawRotation = -8.0F * (1.0F - eased) * sideMultiplier;
      }

      return new float[]{pitchRotation, rollRotation, yawRotation};
   }

   @Unique
   private float[] computeApgLatchedAim(AbstractClientPlayerEntity player, Hand hand, boolean isRightSide, float partialTick) {
      HookPoint hook = isRightSide ? ODMTickHandler.getRightHook() : ODMTickHandler.getLeftHook();
      if (hook != null && hook.active && !hook.isExtending && !hook.isRetracting && hook.position != null) {
         Vec3d eye = player.getCameraPosVec(partialTick);
         Vec3d diff = hook.position.subtract(eye);
         if (diff.lengthSquared() < 1.0E-6) {
            return null;
         } else {
            Vec3d dir = diff.normalize();
            Vec3d forward = player.getRotationVec(partialTick);
            Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
            Vec3d right = forward.crossProduct(worldUp);
            if (right.lengthSquared() < 1.0E-6) {
               return null;
            } else {
               right = right.normalize();
               Vec3d camUp = right.crossProduct(forward).normalize();
               double rightDot = dir.dotProduct(right);
               double upDot = dir.dotProduct(camUp);
               double fwdDot = dir.dotProduct(forward);
               float pitchRel = (float)Math.toDegrees(Math.atan2(upDot, Math.sqrt(rightDot * rightDot + fwdDot * fwdDot)));
               float yawRel = -((float)Math.toDegrees(Math.atan2(rightDot, fwdDot)));
               pitchRel = MathHelper.clamp(pitchRel, -75.0F, 75.0F);
               yawRel = MathHelper.clamp(yawRel, -75.0F, 75.0F);
               return new float[]{pitchRel, yawRel};
            }
         }
      } else {
         return null;
      }
   }

   @Unique
   private float easeInCubic(float t) {
      return t * t * t;
   }

   @Unique
   private float easeOutCubic(float t) {
      return 1.0F - (float)Math.pow(1.0F - t, 3.0);
   }

   @Unique
   private float easeWithAnticipation(float t) {
      float s = 1.7F;
      return t * t * ((s + 1.0F) * t - s);
   }
}
