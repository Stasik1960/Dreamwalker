package daot.mixin.client;

import daot.HomelanderLaserClientHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemRenderer.class)
public abstract class HomelanderLaserHandHideMixin {
   @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"), cancellable = true)
   private void daot$hideHandWhileFiring(
      float partialTick, MatrixStack poseStack, Immediate bufferSource, ClientPlayerEntity localPlayer, int packedLight, CallbackInfo ci
   ) {
      if (localPlayer != null) {
         if (HomelanderLaserClientHandler.activeFirers().contains(localPlayer.getUuid())) {
            ci.cancel();
         }
      }
   }
}
