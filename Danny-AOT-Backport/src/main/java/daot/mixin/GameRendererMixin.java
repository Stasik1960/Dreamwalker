package daot.mixin;

import daot.CameraShakeHandler;
import daot.CameraTiltHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class GameRendererMixin {
   @Shadow
   protected abstract Quaternionf getRotation();

   @Inject(method = "getRotation", at = @At("RETURN"), cancellable = true)
   private void modifyRotation(CallbackInfoReturnable<Quaternionf> cir) {
      MinecraftClient mc = MinecraftClient.getInstance();
      float partialTick = mc.getTickDelta();
      float tilt = CameraTiltHandler.getCurrentTilt(partialTick);
      float speedPitch = CameraTiltHandler.getCurrentPitch(partialTick);
      float shakeX = CameraShakeHandler.getShakeX(partialTick);
      float shakeY = CameraShakeHandler.getShakeY(partialTick);
      if (Math.abs(tilt) > 0.01F || Math.abs(speedPitch) > 0.01F || Math.abs(shakeX) > 0.01F || Math.abs(shakeY) > 0.01F) {
         Quaternionf modified = new Quaternionf((Quaternionfc)cir.getReturnValue());
         if (Math.abs(tilt) > 0.01F) {
            modified.rotateZ((float)Math.toRadians(tilt));
         }

         if (Math.abs(speedPitch) > 0.01F) {
            modified.rotateX((float)Math.toRadians(speedPitch));
         }

         if (Math.abs(shakeX) > 0.01F) {
            modified.rotateX((float)Math.toRadians(shakeX));
         }

         if (Math.abs(shakeY) > 0.01F) {
            modified.rotateY((float)Math.toRadians(shakeY));
         }

         cir.setReturnValue(modified);
      }
   }
}
