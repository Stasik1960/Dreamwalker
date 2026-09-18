package daot.mixin.client;

import daot.HomelanderGrabClientHandler;
import daot.network.HomelanderGrabRotationPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public class HomelanderGrabbedCameraLockMixin {
   @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
   private void daot$grabTurnHook(double yRot, double xRot, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         if ((Object)this == mc.player) {
            if (HomelanderGrabClientHandler.isLocalPlayerGrabbing()) {
               float newYaw = mc.player.getYaw() + (float)yRot * 0.15F;
               float newPitch = MathHelper.clamp(mc.player.getPitch() + (float)xRot * 0.15F, -90.0F, 90.0F);

               try {
                  ClientPlayNetworking.send(new HomelanderGrabRotationPayload(newYaw, newPitch));
               } catch (Exception var10) {
               }
            }

            if (mc.options.getPerspective() == Perspective.FIRST_PERSON) {
               if (HomelanderGrabClientHandler.findGrabberOf(mc.player.getId()) != null) {
                  ci.cancel();
               }
            }
         }
      }
   }

   @Inject(method = "getYaw(F)F", at = @At("HEAD"), cancellable = true)
   private void daot$grabbedViewYaw(float partialTick, CallbackInfoReturnable<Float> cir) {
      if (this.shouldOverrideCamera()) {
         if (HomelanderGrabClientHandler.hasGrabberRotation()) {
            float yaw = HomelanderGrabClientHandler.getGrabberYawInterpolated() + 180.0F;
            cir.setReturnValue(MathHelper.wrapDegrees(yaw));
         }
      }
   }

   @Inject(method = "getPitch(F)F", at = @At("HEAD"), cancellable = true)
   private void daot$grabbedViewPitch(float partialTick, CallbackInfoReturnable<Float> cir) {
      if (this.shouldOverrideCamera()) {
         if (HomelanderGrabClientHandler.hasGrabberRotation()) {
            float pitch = -HomelanderGrabClientHandler.getGrabberPitchInterpolated();
            cir.setReturnValue(MathHelper.clamp(pitch, -90.0F, 90.0F));
         }
      }
   }

   private boolean shouldOverrideCamera() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         return false;
      } else if ((Object)this != mc.player) {
         return false;
      } else {
         return mc.options.getPerspective() != Perspective.FIRST_PERSON ? false : HomelanderGrabClientHandler.findGrabberOf(mc.player.getId()) != null;
      }
   }
}

