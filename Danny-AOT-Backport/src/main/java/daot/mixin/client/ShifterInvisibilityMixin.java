package daot.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(EntityRenderDispatcher.class)
public abstract class ShifterInvisibilityMixin {
   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void daot$hideShiftedPlayers(
      Entity entity,
      double x,
      double y,
      double z,
      float rotationYaw,
      float partialTicks,
      MatrixStack poseStack,
      VertexConsumerProvider bufferSource,
      int packedLight,
      CallbackInfo ci
   ) {
      if (entity instanceof PlayerEntity player && player.isInvisible() && player.hasVehicle()) {
         ci.cancel();
      }
   }
}
