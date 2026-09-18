package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class ArmoredTitanEyeRenderer extends EntityRenderer<ArmoredTitanEyeEntity> {
   public ArmoredTitanEyeRenderer(Context context) {
      super(context);
   }

   public void render(ArmoredTitanEyeEntity entity, float entityYaw, float partialTicks, MatrixStack poseStack, VertexConsumerProvider buffer, int packedLight) {
      super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
   }

   public Identifier getTexture(ArmoredTitanEyeEntity entity) {
      return new Identifier("textures/misc/white.png");
   }
}
