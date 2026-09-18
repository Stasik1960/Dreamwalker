package daot.mixin;

import daot.AtrainSpeedClientState;
import daot.BloodlineClientData;
import daot.BloodlineType;
import daot.BoostFovHandler;
import daot.HomelanderLaserClientHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public class FovMixin {
   private static final float HOMELANDER_LASER_FOV_PEAK = 12.0F;
   private static final float ATRAIN_SPEED_FOV_PEAK = 10.0F;
   private static final float FOV_RAMP_RATE = 6.0F;
   private static float currentLaserFovBump = 0.0F;
   private static float currentAtrainFovBump = 0.0F;
   private static long lastFrameNanos = -1L;

   @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
   private void modifyFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
      float total = BoostFovHandler.getFovModifier(partialTick);
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         long now = System.nanoTime();
         float dt = lastFrameNanos > 0L ? Math.min(0.1F, (float)(now - lastFrameNanos) / 1.0E9F) : 0.016666668F;
         lastFrameNanos = now;
         float smoothing = 1.0F - (float)Math.exp(-dt * 6.0F);
         boolean firing = HomelanderLaserClientHandler.activeFirers().contains(mc.player.getUuid());
         float laserTarget = firing ? 12.0F : 0.0F;
         currentLaserFovBump = currentLaserFovBump + (laserTarget - currentLaserFovBump) * smoothing;
         if (currentLaserFovBump < 0.05F && laserTarget == 0.0F) {
            currentLaserFovBump = 0.0F;
         }

         total += currentLaserFovBump;
         boolean atrainBoosted = BloodlineClientData.get(mc.player.getUuid()) == BloodlineType.ATRAIN
            && AtrainSpeedClientState.isActive(mc.player.getUuid())
            && mc.player.isSprinting();
         float atrainTarget = atrainBoosted ? 10.0F : 0.0F;
         currentAtrainFovBump = currentAtrainFovBump + (atrainTarget - currentAtrainFovBump) * smoothing;
         if (currentAtrainFovBump < 0.05F && atrainTarget == 0.0F) {
            currentAtrainFovBump = 0.0F;
         }

         total += currentAtrainFovBump;
      }

      if (Math.abs(total) > 0.01F) {
         double originalFov = (Double)cir.getReturnValue();
         cir.setReturnValue(originalFov + total);
      }
   }
}
