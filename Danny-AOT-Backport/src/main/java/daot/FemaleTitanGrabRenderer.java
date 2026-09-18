package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class FemaleTitanGrabRenderer extends EntityRenderer<FemaleTitanGrabEntity> {
   public FemaleTitanGrabRenderer(Context context) {
      super(context);
   }

   public void render(
      FemaleTitanGrabEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
   }

   public Identifier getTexture(FemaleTitanGrabEntity entity) {
      return new Identifier("textures/misc/white.png");
   }
}
