package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

@Environment(EnvType.CLIENT)
public class RegimentBannerRenderer implements net.minecraft.client.render.block.entity.BlockEntityRenderer<RegimentBannerBlockEntity> {
   private final GeoBlockRenderer<RegimentBannerBlockEntity> delegate = new GeoBlockRenderer<>(new RegimentBannerModel());
   public RegimentBannerRenderer(Context context) {}

   public void render(
      RegimentBannerBlockEntity blockEntity, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight, int packedOverlay
   ) {
      BlockState state = blockEntity.getCachedState();
      poseStack.push();
      if (state.getBlock() instanceof RegimentBannerBlock) {
         int rotation = state.get(RegimentBannerBlock.ROTATION);
         float angle = -rotation * 22.5F;
         poseStack.translate(0.5, 0.0, 0.5);
         poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
         poseStack.translate(0.0, 0.3, 0.0);
         poseStack.scale(0.7F, 0.7F, 0.7F);
         poseStack.translate(-0.5, 0.0, -0.5);
      } else if (state.getBlock() instanceof WallRegimentBannerBlock) {
         Direction facing = state.get(WallRegimentBannerBlock.FACING);
         double wallX = -facing.getOffsetX() * 0.5;
         double wallZ = -facing.getOffsetZ() * 0.5;
         poseStack.translate(0.5 + wallX, -0.45, 0.5 + wallZ);
         poseStack.scale(0.7F, 0.7F, 0.7F);
         poseStack.translate(-0.5, 0.0, -0.5);
      }

      delegate.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
      poseStack.pop();
   }
}
