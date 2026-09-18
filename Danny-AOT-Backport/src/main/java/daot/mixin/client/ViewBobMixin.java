package daot.mixin.client;

import daot.PushAuraClientData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public class ViewBobMixin {
   @Shadow
   @Final
   MinecraftClient client;

   @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
   private void dannysaot$slowViewBob(MatrixStack poseStack, float partialTick, CallbackInfo ci) {
      if (this.client.getCameraEntity() instanceof PlayerEntity player) {
         double factor = PushAuraClientData.getAnimFreezeFactor(player);
         if (!(factor >= 1.0)) {
            float walked = player.horizontalSpeed - player.prevHorizontalSpeed;
            float walkInter = -(player.horizontalSpeed + walked * partialTick);
            float bobMag = MathHelper.lerp(partialTick, player.prevStrideDistance, player.strideDistance) * (float)factor;
            poseStack.translate(
               MathHelper.sin(walkInter * (float) Math.PI) * bobMag * 0.5F, -Math.abs(MathHelper.cos(walkInter * (float) Math.PI) * bobMag), 0.0F
            );
            poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(walkInter * (float) Math.PI) * bobMag * 3.0F));
            poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.abs(MathHelper.cos(walkInter * (float) Math.PI - 0.2F) * bobMag) * 5.0F));
            ci.cancel();
         }
      }
   }
}
