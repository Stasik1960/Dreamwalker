package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@Environment(EnvType.CLIENT)
public class RegimentBannerItemRenderer extends GeoItemRenderer<RegimentBannerItem> {
   private static final Identifier SPRITE_GARRISON = new Identifier("dannys-aot", "textures/item/regiment_banner_garrison_item.png");
   private static final Identifier SPRITE_MILITARY = new Identifier("dannys-aot", "textures/item/regiment_banner_military_item.png");
   private static final Identifier SPRITE_SCOUT = new Identifier("dannys-aot", "textures/item/regiment_banner_scout_item.png");
   private static final Identifier SPRITE_TRAINING = new Identifier("dannys-aot", "textures/item/regiment_banner_training_item.png");
   private static final Identifier SPRITE_GOLD_CLOAK = new Identifier("dannys-aot", "textures/item/regiment_banner_gold_item.png");

   public RegimentBannerItemRenderer() {
      super(new RegimentBannerItemModel());
   }

   public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
      if (mode == ModelTransformationMode.GUI) {
         matrices.pop();
         matrices.push();
         this.renderSprite(stack, matrices, vertexConsumers, 15728880);
      } else {
         super.render(stack, mode, matrices, vertexConsumers, light, overlay);
      }
   }

   public Identifier getTextureLocation(RegimentBannerItem item) {
      return RegimentBannerItemModel.getTextureForType(item.getRegimentType());
   }

   private void renderSprite(ItemStack stack, MatrixStack poseStack, VertexConsumerProvider bufferSource, int light) {
      Identifier tex = this.getSpriteTexture(stack);
      VertexConsumer vc = bufferSource.getBuffer(RenderLayer.getEntityCutoutNoCull(tex));
      Entry pose = poseStack.peek();
      Matrix4f matrix = pose.getPositionMatrix();
      float h = 0.5F;
      vc.vertex(matrix, -h, h, 0.0F)
         .color(255, 255, 255, 255)
         .texture(0.0F, 0.0F)
         .overlay(OverlayTexture.DEFAULT_UV)
         .light(light)
         .normal(pose.getNormalMatrix(), 0.0F, 0.0F, 1.0F).next();
      vc.vertex(matrix, -h, -h, 0.0F)
         .color(255, 255, 255, 255)
         .texture(0.0F, 1.0F)
         .overlay(OverlayTexture.DEFAULT_UV)
         .light(light)
         .normal(pose.getNormalMatrix(), 0.0F, 0.0F, 1.0F).next();
      vc.vertex(matrix, h, -h, 0.0F)
         .color(255, 255, 255, 255)
         .texture(1.0F, 1.0F)
         .overlay(OverlayTexture.DEFAULT_UV)
         .light(light)
         .normal(pose.getNormalMatrix(), 0.0F, 0.0F, 1.0F).next();
      vc.vertex(matrix, h, h, 0.0F)
         .color(255, 255, 255, 255)
         .texture(1.0F, 0.0F)
         .overlay(OverlayTexture.DEFAULT_UV)
         .light(light)
         .normal(pose.getNormalMatrix(), 0.0F, 0.0F, 1.0F).next();
   }

   private Identifier getSpriteTexture(ItemStack stack) {
      if (stack.getItem() instanceof RegimentBannerItem bannerItem) {
         return switch (bannerItem.getRegimentType()) {
            case GARRISON -> SPRITE_GARRISON;
            case MILITARY_POLICE -> SPRITE_MILITARY;
            case SCOUT -> SPRITE_SCOUT;
            case TRAINING -> SPRITE_TRAINING;
            case GOLD_CLOAK -> SPRITE_GOLD_CLOAK;
         };
      } else {
         return SPRITE_GARRISON;
      }
   }
}
