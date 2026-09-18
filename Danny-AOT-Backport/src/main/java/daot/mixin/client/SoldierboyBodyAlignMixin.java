package daot.mixin.client;

import daot.BloodlineClientData;
import daot.BloodlineType;
import daot.SoldierboyPlayerAnimationHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class SoldierboyBodyAlignMixin {
   @Inject(method = "setupTransforms(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V", at = @At("TAIL"))
   private void daot$soldierboyBodyAlign(LivingEntity entity, MatrixStack poseStack, float bob, float bodyYaw, float partialTick, CallbackInfo ci) {
      if (entity instanceof AbstractClientPlayerEntity player) {
         if (BloodlineClientData.get(player.getUuid()) == BloodlineType.SOLDIERBOY) {
            if (SoldierboyPlayerAnimationHandler.isInChestAbilityState(player.getUuid())) {
               float headYaw = MathHelper.lerpAngleDegrees(partialTick, entity.prevHeadYaw, entity.headYaw);
               poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw - headYaw));
            }
         }
      }
   }
}
