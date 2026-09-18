package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

@Environment(EnvType.CLIENT)
public class RedScarfRenderer extends GeoArmorRenderer<RedScarfItem> {
   public RedScarfRenderer() {
      super(new RedScarfModel());
   }

   protected void applyBoneVisibilityBySlot(EquipmentSlot currentSlot) {
      this.setVisible(true);
   }

   public void preRender(
      MatrixStack poseStack,
      RedScarfItem animatable,
      BakedGeoModel model,
      @Nullable VertexConsumerProvider bufferSource,
      @Nullable VertexConsumer buffer,
      boolean isReRender,
      float partialTick,
      int packedLight,
      int packedOverlay,
      float bpRed, float bpGreen, float bpBlue, float bpAlpha
   ) {
      int colour = daot.compat.RenderColors.pack(bpRed,bpGreen,bpBlue,bpAlpha);
      super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,daot.compat.RenderColors.red(colour), daot.compat.RenderColors.green(colour), daot.compat.RenderColors.blue(colour), daot.compat.RenderColors.alpha(colour));
      boolean slim = this.currentEntity instanceof AbstractClientPlayerEntity player && "slim".equals(player.getModel());
      float scaleX = slim ? 0.75F : 1.0F;
      if (this.rightArm != null) {
         this.rightArm.setScaleX(scaleX);
      }

      if (this.leftArm != null) {
         this.leftArm.setScaleX(scaleX);
      }
   }
}
