package daot.mixin.client;

import daot.ShiftLightningClientTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LightningEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LightningEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Environment(EnvType.CLIENT)
@Mixin(LightningEntityRenderer.class)
public abstract class LightningBoltRendererMixin {
   @Unique
   private static boolean daot$currentBoltYellow;
   @Unique
   private static final float DAOT_YELLOW_R = 0.98F;
   @Unique
   private static final float DAOT_YELLOW_G = 0.85F;
   @Unique
   private static final float DAOT_YELLOW_B = 0.1F;

   @Inject(method = "render(Lnet/minecraft/entity/LightningEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
   private void daot$captureShiftBolt(
      LightningEntity bolt, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider buffer, int packedLight, CallbackInfo ci
   ) {
      daot$currentBoltYellow = ShiftLightningClientTracker.isYellow(bolt.getId());
   }

   @ModifyArgs(
      method = "drawBranch(Lorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumer;FFIFFFFFFFZZZZ)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumer;color(FFFF)Lnet/minecraft/client/render/VertexConsumer;")
   )
   private static void daot$recolorShiftBolt(Args args) {
      if (daot$currentBoltYellow) {
         args.set(0, 0.98F);
         args.set(1, 0.85F);
         args.set(2, 0.1F);
      }
   }
}
