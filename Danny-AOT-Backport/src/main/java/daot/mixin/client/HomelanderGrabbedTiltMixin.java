package daot.mixin.client;

import daot.HomelanderGrabClientHandler;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class HomelanderGrabbedTiltMixin {
   @Inject(method = "setupTransforms(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V", at = @At("TAIL"))
   private void daot$grabbedTilt(LivingEntity entity, MatrixStack poseStack, float bob, float bodyYaw, float partialTick, CallbackInfo ci) {
      if (entity instanceof AbstractClientPlayerEntity victim) {
         UUID grabberUuid = HomelanderGrabClientHandler.findGrabberOf(victim.getId());
         if (grabberUuid != null) {
            if (victim.getWorld() != null) {
               PlayerEntity grabber = victim.getWorld().getPlayerByUuid(grabberUuid);
               if (grabber != null) {
                  double vx = MathHelper.lerp((double)partialTick, victim.lastRenderX, victim.getX());
                  double vy = MathHelper.lerp((double)partialTick, victim.lastRenderY, victim.getY());
                  double vz = MathHelper.lerp((double)partialTick, victim.lastRenderZ, victim.getZ());
                  double gx = MathHelper.lerp((double)partialTick, grabber.lastRenderX, grabber.getX());
                  double gy = MathHelper.lerp((double)partialTick, grabber.lastRenderY, grabber.getY());
                  double gz = MathHelper.lerp((double)partialTick, grabber.lastRenderZ, grabber.getZ());
                  double dx = gx - vx;
                  double dy = gy + grabber.getHeight() * 0.5 - (vy + victim.getHeight() * 0.5);
                  double dz = gz - vz;
                  double horiz = Math.sqrt(dx * dx + dz * dz);
                  if (!(horiz < 1.0E-4) || !(Math.abs(dy) < 1.0E-4)) {
                     float aimYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
                     poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw - aimYaw));
                     float aimPitch = (float)Math.toDegrees(Math.atan2(dy, horiz));
                     poseStack.translate(0.0, 0.75, 0.0);
                     poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(aimPitch));
                     poseStack.translate(0.0, -0.75, 0.0);
                  }
               }
            }
         }
      }
   }
}
