package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class BeastTitanNapeRenderer extends EntityRenderer<BeastTitanNapeEntity> {
   public BeastTitanNapeRenderer(Context context) {
      super(context);
   }

   public void render(
      BeastTitanNapeEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
   }

   public Identifier getTexture(BeastTitanNapeEntity entity) {
      return new Identifier("textures/misc/white.png");
   }
}
