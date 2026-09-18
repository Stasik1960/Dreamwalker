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
public class ArmorPotionItemRenderer extends GeoItemRenderer<ArmorPotionItem> {
   private static final Identifier SPRITE = new Identifier("dannys-aot", "textures/item/armor_potion_item.png");

   public ArmorPotionItemRenderer() {
      super(new ArmorPotionItemModel());
   }

   public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
      if (mode == ModelTransformationMode.GUI && stack.getItem() instanceof ArmorPotionItem) {
         matrices.pop();
         matrices.push();
         this.renderSprite(SPRITE, matrices, vertexConsumers, 15728880);
      } else {
         super.render(stack, mode, matrices, vertexConsumers, light, overlay);
      }
   }

   private void renderSprite(Identifier tex, MatrixStack poseStack, VertexConsumerProvider bufferSource, int light) {
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
}
