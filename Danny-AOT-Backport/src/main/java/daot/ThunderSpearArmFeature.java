package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

@Environment(EnvType.CLIENT)
public class ThunderSpearArmFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/item/thunder_spear.png");
   private ThunderSpearAttachmentRenderer.ThunderSpearAttachmentModel geoModel;

   public ThunderSpearArmFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> renderer) {
      super(renderer);
   }

   private ThunderSpearAttachmentRenderer.ThunderSpearAttachmentModel getGeoModel() {
      if (this.geoModel == null) {
         this.geoModel = new ThunderSpearAttachmentRenderer.ThunderSpearAttachmentModel();
      }

      return this.geoModel;
   }

   public void render(
      MatrixStack poseStack,
      VertexConsumerProvider bufferSource,
      int packedLight,
      AbstractClientPlayerEntity player,
      float limbSwing,
      float limbSwingAmount,
      float partialTick,
      float ageInTicks,
      float netHeadYaw,
      float headPitch
   ) {
      if (!player.isInvisible()) {
         ItemStack mainHand = player.getMainHandStack();
         ItemStack offHand = player.getOffHandStack();
         boolean mainHasSpear = mainHand.getItem() instanceof BladeItem && BladeItem.hasThunderSpear(mainHand);
         boolean offHasSpear = offHand.getItem() instanceof BladeItem && BladeItem.hasThunderSpear(offHand);
         if (mainHasSpear || offHasSpear) {
            boolean isRightHanded = player.getMainArm() == Arm.RIGHT;
            if (mainHasSpear) {
               this.renderSpearOnArm(poseStack, bufferSource, packedLight, isRightHanded);
            }

            if (offHasSpear) {
               this.renderSpearOnArm(poseStack, bufferSource, packedLight, !isRightHanded);
            }
         }
      }
   }

   private void renderSpearOnArm(MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight, boolean rightArm) {
      ThunderSpearAttachmentRenderer.ThunderSpearAttachmentModel model = this.getGeoModel();
      BakedGeoModel bakedModel = model.getBakedModel(model.getModelResource(null));
      if (bakedModel != null) {
         GeoBone groupBone = (GeoBone)bakedModel.getBone("group").orElse(null);
         GeoBone boneBone = (GeoBone)bakedModel.getBone("bone").orElse(null);
         GeoBone thunderSpearBone = (GeoBone)bakedModel.getBone("thunder_spear").orElse(null);
         if (groupBone != null) {
            groupBone.setHidden(true);
         }

         if (boneBone != null) {
            boneBone.setHidden(true);
         }

         if (thunderSpearBone != null) {
            thunderSpearBone.setHidden(false);
         }

         ModelPart arm = rightArm ? this.getContextModel().rightArm : this.getContextModel().leftArm;
         poseStack.push();
         arm.rotate(poseStack);
         float side = rightArm ? -1.0F : 1.0F;
         poseStack.translate(0.2 * side, 0.15, 0.0);
         poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
         poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0F * side));
         float scale = 1.0F;
         poseStack.scale(scale, scale, scale);
         RenderLayer renderType = RenderLayer.getEntityCutoutNoCull(TEXTURE);
         VertexConsumer buffer = bufferSource.getBuffer(renderType);
         GeoModelHelper.renderModel(poseStack, bakedModel, buffer, packedLight, OverlayTexture.DEFAULT_UV, -1);
         poseStack.pop();
         if (groupBone != null) {
            groupBone.setHidden(false);
         }

         if (boneBone != null) {
            boneBone.setHidden(false);
         }
      }
   }
}
