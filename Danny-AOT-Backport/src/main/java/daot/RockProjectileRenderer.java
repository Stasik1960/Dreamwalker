package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Environment(EnvType.CLIENT)
public class RockProjectileRenderer extends GeoEntityRenderer<RockProjectileEntity> {
   public RockProjectileRenderer(Context context) {
      super(context, new RockProjectileModel());
   }

   public void render(
      RockProjectileEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
      if (entity.isVisuallyScaled()) {
         float scale = entity.getRockScale();
         poseStack.push();
         poseStack.scale(scale, scale, scale);
         super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
         poseStack.pop();
      } else {
         super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      }
   }
}
