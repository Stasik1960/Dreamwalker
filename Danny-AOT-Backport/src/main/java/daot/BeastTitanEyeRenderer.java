package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class BeastTitanEyeRenderer extends EntityRenderer<BeastTitanEyeEntity> {
   public BeastTitanEyeRenderer(Context context) {
      super(context);
   }

   public void render(
      BeastTitanEyeEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
   }

   public Identifier getTexture(BeastTitanEyeEntity entity) {
      return new Identifier("textures/misc/white.png");
   }
}
