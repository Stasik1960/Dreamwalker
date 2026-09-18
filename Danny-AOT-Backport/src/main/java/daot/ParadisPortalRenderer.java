package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class ParadisPortalRenderer implements BlockEntityRenderer<ParadisPortalBlockEntity> {
   public ParadisPortalRenderer(Context context) {
   }

   public void render(
      ParadisPortalBlockEntity blockEntity, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight, int packedOverlay
   ) {
      Matrix4f matrix = poseStack.peek().getPositionMatrix();
      this.renderCube(blockEntity, matrix, bufferSource.getBuffer(RenderLayer.getEndPortal()));
   }

   private void renderCube(ParadisPortalBlockEntity blockEntity, Matrix4f matrix, VertexConsumer consumer) {
      float minY = 0.375F;
      float maxY = 0.75F;
      this.renderFace(blockEntity, matrix, consumer, 0.0F, 1.0F, maxY, maxY, 1.0F, 1.0F, 0.0F, 0.0F, Direction.UP);
      this.renderFace(blockEntity, matrix, consumer, 0.0F, 1.0F, minY, minY, 0.0F, 0.0F, 1.0F, 1.0F, Direction.DOWN);
   }

   private void renderFace(
      ParadisPortalBlockEntity blockEntity,
      Matrix4f matrix,
      VertexConsumer consumer,
      float x0,
      float x1,
      float y0,
      float y1,
      float z0,
      float z1,
      float z2,
      float z3,
      Direction direction
   ) {
      consumer.vertex(matrix, x0, y0, z0).next();
      consumer.vertex(matrix, x1, y0, z1).next();
      consumer.vertex(matrix, x1, y1, z2).next();
      consumer.vertex(matrix, x0, y1, z3).next();
   }
}
