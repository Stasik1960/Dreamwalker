package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

@Environment(EnvType.CLIENT)
public class GibRenderer extends EntityRenderer<GibEntity> {
   private static final float MAX_SCALE = 0.32F;

   public GibRenderer(Context ctx) {
      super(ctx);
   }

   public void render(GibEntity entity, float entityYaw, float partialTick, MatrixStack pose, VertexConsumerProvider buf, int packedLight) {
      pose.push();
      int spin = entity.getSpin();
      float yawDeg = spin * 47 % 360;
      float pitchDeg = spin * 73 % 360;
      float tumble = (entity.getGibAge() + partialTick) * (4.0F + (spin & 7));
      float scale = 0.32F * entity.getVisualScale();
      if (scale <= 0.001F) {
         pose.pop();
         super.render(entity, entityYaw, partialTick, pose, buf, packedLight);
      } else {
         pose.scale(scale, scale, scale);
         pose.translate(-0.5F, 0.0F, -0.5F);
         pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg + tumble));
         pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDeg + tumble * 0.7F));
         pose.translate(-0.5F, -0.5F, -0.5F);
         MinecraftClient.getInstance()
            .getBlockRenderManager()
            .renderBlockAsEntity(Blocks.NETHERRACK.getDefaultState(), pose, buf, packedLight, OverlayTexture.DEFAULT_UV);
         pose.pop();
         super.render(entity, entityYaw, partialTick, pose, buf, packedLight);
      }
   }

   public Identifier getTexture(GibEntity entity) {
      return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
   }
}
