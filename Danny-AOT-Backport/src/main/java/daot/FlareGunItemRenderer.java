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
public class FlareGunItemRenderer extends GeoItemRenderer<FlareGunItem> {
   private static final Identifier SPRITE_EMPTY = new Identifier("dannys-aot", "textures/item/flare_gun_empty_item.png");
   private static final Identifier[] SPRITE_COLORS = new Identifier[]{
      new Identifier("dannys-aot", "textures/item/flare_gun_red_item.png"),
      new Identifier("dannys-aot", "textures/item/flare_gun_black_item.png"),
      new Identifier("dannys-aot", "textures/item/flare_gun_purple_item.png"),
      new Identifier("dannys-aot", "textures/item/flare_gun_blue_item.png"),
      new Identifier("dannys-aot", "textures/item/flare_gun_green_item.png"),
      new Identifier("dannys-aot", "textures/item/flare_gun_yellow_item.png")
   };

   public FlareGunItemRenderer() {
      super(new FlareGunItemModel());
   }

   public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
      if (mode == ModelTransformationMode.GUI) {
         matrices.pop();
         matrices.push();
         this.renderSprite(stack, matrices, vertexConsumers, 15728880);
      } else {
         FlareGunItemModel.currentColorOrdinal = FlareGunItem.getLoadedColorOrdinal(stack);
         super.render(stack, mode, matrices, vertexConsumers, light, overlay);
      }
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
      int colorOrd = FlareGunItem.getLoadedColorOrdinal(stack);
      return colorOrd >= 0 && colorOrd < SPRITE_COLORS.length ? SPRITE_COLORS[colorOrd] : SPRITE_EMPTY;
   }
}
