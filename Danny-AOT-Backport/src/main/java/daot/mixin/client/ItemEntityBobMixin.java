package daot.mixin.client;

import daot.PushAuraClientData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemEntityRenderer.class)
public class ItemEntityBobMixin {
   @Unique
   private ItemEntity dannysaot_currentEntity;

   @Inject(method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
   private void dannysaot_captureEntity(
      ItemEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider buffer, int packedLight, CallbackInfo ci
   ) {
      this.dannysaot_currentEntity = entity;
   }

   @Redirect(
      method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;sin(F)F")
   )
   private float dannysaot_slowBob(float input) {
      float result = MathHelper.sin(input);
      if (this.dannysaot_currentEntity != null) {
         double factor = PushAuraClientData.getAnimFreezeFactor(this.dannysaot_currentEntity);
         if (factor < 1.0) {
            return result * (float)factor;
         }
      }

      return result;
   }
}
