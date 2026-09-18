package daot.mixin.client;

import daot.GeassClientState;
import daot.network.MindControlRotationPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public class GeassCameraLockMixin {
   @Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
   private void hideControllerFromPicking(CallbackInfoReturnable<Boolean> cir) {
      if (GeassClientState.isMindControlController) {
         MinecraftClient mc = MinecraftClient.getInstance();
         Entity self = (Entity)(Object)this;
         if (self == mc.player) {
            cir.setReturnValue(false);
         } else {
            int targetId = GeassClientState.mindControlTargetEntityId;
            if (targetId != -1 && self.getId() == targetId) {
               cir.setReturnValue(false);
            }
         }
      }
   }

   @Inject(method = "collidesWith", at = @At("HEAD"), cancellable = true)
   private void noCollisionController(Entity other, CallbackInfoReturnable<Boolean> cir) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && GeassClientState.isMindControlController) {
         if ((Object)this == mc.player) {
            cir.setReturnValue(false);
         } else {
            if (other == mc.player) {
               cir.setReturnValue(false);
            }
         }
      }
   }

   @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
   private void mindControlVisibility(CallbackInfoReturnable<Boolean> cir) {
      if (GeassClientState.isMindControlController) {
         MinecraftClient mc = MinecraftClient.getInstance();
         Entity self = (Entity)(Object)this;
         if (self == mc.player) {
            cir.setReturnValue(false);
         } else {
            int targetId = GeassClientState.mindControlTargetEntityId;
            if (targetId != -1 && self.getId() == targetId) {
               cir.setReturnValue(true);
            }
         }
      }
   }

   @Inject(method = "getYaw(F)F", at = @At("HEAD"), cancellable = true)
   private void smoothTargetYaw(float partialTick, CallbackInfoReturnable<Float> cir) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if ((Object)this == mc.player && GeassClientState.isMindControlTarget && GeassClientState.mcInputPayloadTimeNano > 0L) {
         long elapsedNano = System.nanoTime() - GeassClientState.mcInputPayloadTimeNano;
         float t = Math.min((float)elapsedNano / 5.0E7F, 1.0F);
         float prev = GeassClientState.prevMcInputYaw;
         float current = GeassClientState.mcInputYaw;
         cir.setReturnValue(prev + MathHelper.wrapDegrees(current - prev) * t);
      }
   }

   @Inject(method = "getPitch(F)F", at = @At("HEAD"), cancellable = true)
   private void smoothTargetPitch(float partialTick, CallbackInfoReturnable<Float> cir) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if ((Object)this == mc.player && GeassClientState.isMindControlTarget && GeassClientState.mcInputPayloadTimeNano > 0L) {
         long elapsedNano = System.nanoTime() - GeassClientState.mcInputPayloadTimeNano;
         float t = Math.min((float)elapsedNano / 5.0E7F, 1.0F);
         float prev = GeassClientState.prevMcInputPitch;
         float current = GeassClientState.mcInputPitch;
         cir.setReturnValue(MathHelper.clamp(prev + (current - prev) * t, -90.0F, 90.0F));
      }
   }

   @Inject(method = "updateTrackedPositionAndAngles(DDDFFIZ)V", at = @At("HEAD"), cancellable = true)
   private void snapCameraPosition(double x, double y, double z, float yRot, float xRot, int steps, boolean interpolate, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && GeassClientState.isMindControlController && mc.cameraEntity == (Object)this && mc.cameraEntity != mc.player) {
         Entity self = (Entity)(Object)this;
         self.setPosition(x, y, z);
         ci.cancel();
      }
   }

   @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
   private void lockCameraIfGeassed(double yRot, double xRot, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if ((Object)this == mc.player && GeassClientState.isMindControlController) {
         float newYaw = mc.player.getYaw() + (float)yRot * 0.15F;
         float newPitch = MathHelper.clamp(mc.player.getPitch() + (float)xRot * 0.15F, -90.0F, 90.0F);

         try {
            ClientPlayNetworking.send(new MindControlRotationPayload(newYaw, newPitch));
         } catch (Exception var10) {
         }
      } else if ((Object)this == mc.player) {
         if (GeassClientState.isMindControlTarget) {
            ci.cancel();
         } else if (GeassClientState.isCameraLocked()) {
            float[] desired = GeassClientState.getDesiredRotation();
            if (desired != null) {
               Entity self = (Entity)(Object)this;
               self.setYaw(desired[0]);
               self.setPitch(desired[1]);
            }

            ci.cancel();
         }
      }
   }
}

