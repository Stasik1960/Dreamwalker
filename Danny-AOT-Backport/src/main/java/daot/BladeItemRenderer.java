package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@Environment(EnvType.CLIENT)
public class BladeItemRenderer extends GeoItemRenderer<BladeItem> {
   private final BladeItemModel bladeModel = (BladeItemModel)this.model;
   private static final Identifier SPRITE_GRIP = new Identifier("dannys-aot", "textures/item/odm_grip.png");
   private static final Identifier SPRITE_FRESH = new Identifier("dannys-aot", "textures/item/odm_blade_and_handle.png");
   private static final Identifier SPRITE_DAMAGED = new Identifier("dannys-aot", "textures/item/odm_blade_and_handle_damaged.png");
   private static final Identifier SPRITE_BROKEN = new Identifier("dannys-aot", "textures/item/odm_blade_and_handle_broken.png");

   public BladeItemRenderer() {
      super(new BladeItemModel());
   }

   public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
      if (stack.getItem() instanceof BladeItem) {
         if (MinecraftClient.getInstance().world != null) {
            BladeItem.updateEjectAnimationState(stack, MinecraftClient.getInstance().world.getTime());
         }

         BladeItem.BladeState state = BladeItem.getVisualBladeState(stack);
         this.bladeModel.setCurrentState(state);
         this.bladeModel.currentUseKirito = isKirito(stack);
         this.bladeModel.currentUseNeedle = isNeedle(stack);
         this.bladeModel.currentUseHyper = isHyper(stack);
         if (mode == ModelTransformationMode.GUI) {
            matrices.pop();
            matrices.push();
            this.renderSprite(getSpriteForState(state), matrices, vertexConsumers, 15728880);
            return;
         }

         if (state == BladeItem.BladeState.EMPTY && mode == ModelTransformationMode.FIXED) {
            matrices.push();
            matrices.scale(1.9160156F, 1.9160156F, 1.9160156F);
            matrices.translate(-0.365, 0.11, 0.0);
            super.render(stack, mode, matrices, vertexConsumers, light, overlay);
            matrices.pop();
            return;
         }

         if (state == BladeItem.BladeState.EMPTY && mode == ModelTransformationMode.GROUND) {
            matrices.push();
            matrices.translate(0.0, 0.078, 0.0);
            super.render(stack, mode, matrices, vertexConsumers, light, overlay);
            matrices.pop();
            return;
         }
      }

      super.render(stack, mode, matrices, vertexConsumers, light, overlay);
      if (stack.getItem() instanceof BladeItem
         && (mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND || mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND)) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null) {
            boolean mainIsRight = mc.player.getMainArm() == Arm.RIGHT;
            boolean isRightHand = mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
            Hand hand = isRightHand == mainIsRight ? Hand.MAIN_HAND : Hand.OFF_HAND;
            float partialTick = mc.getTickDelta();
            ThunderSpearAttachmentRenderer.render(matrices, vertexConsumers, light, hand, isRightHand, partialTick);
         }
      }
   }

   private static Identifier getSpriteForState(BladeItem.BladeState state) {
      return switch (state) {
         case EMPTY -> SPRITE_GRIP;
         case FRESH -> SPRITE_FRESH;
         case CHIPPED_1, CHIPPED_2 -> SPRITE_DAMAGED;
         case CHIPPED_3 -> SPRITE_BROKEN;
      };
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

   public void actuallyRender(
      MatrixStack poseStack,
      BladeItem animatable,
      BakedGeoModel model,
      RenderLayer renderType,
      VertexConsumerProvider bufferSource,
      VertexConsumer buffer,
      boolean isReRender,
      float partialTick,
      int packedLight,
      int packedOverlay,
      float bpRed, float bpGreen, float bpBlue, float bpAlpha
   ) {
      int colour = daot.compat.RenderColors.pack(bpRed,bpGreen,bpBlue,bpAlpha);
      ItemStack stack = this.currentItemStack;
      if (stack != null && stack.getItem() instanceof BladeItem) {
         if (MinecraftClient.getInstance().world != null) {
            BladeItem.updateEjectAnimationState(stack, MinecraftClient.getInstance().world.getTime());
         }

         BladeItem.BladeState state = BladeItem.getVisualBladeState(stack);
         this.bladeModel.setCurrentState(state);
         this.bladeModel.currentUseKirito = isKirito(stack);
         this.bladeModel.currentUseNeedle = isNeedle(stack);
         this.bladeModel.currentUseHyper = isHyper(stack);
      }

      super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,daot.compat.RenderColors.red(colour), daot.compat.RenderColors.green(colour), daot.compat.RenderColors.blue(colour), daot.compat.RenderColors.alpha(colour));
   }

   public Identifier getTextureLocation(BladeItem animatable) {
      ItemStack stack = this.currentItemStack;
      if (stack != null && stack.getItem() instanceof BladeItem) {
         if (MinecraftClient.getInstance().world != null) {
            BladeItem.updateEjectAnimationState(stack, MinecraftClient.getInstance().world.getTime());
         }

         BladeItem.BladeState state = BladeItem.getVisualBladeState(stack);
         boolean kirito = isKirito(stack);
         boolean needle = isNeedle(stack);
         boolean hyper = isHyper(stack);
         this.bladeModel.setCurrentState(state);
         this.bladeModel.currentUseKirito = kirito;
         this.bladeModel.currentUseNeedle = needle;
         this.bladeModel.currentUseHyper = hyper;
         if (kirito) {
            return BladeItemModel.getKiritoTextureForState(state);
         } else if (needle) {
            return BladeItemModel.getNeedleTextureForState(state);
         } else {
            return hyper ? BladeItemModel.getHyperTextureForState(state) : BladeItemModel.getTextureForState(state);
         }
      } else {
         return BladeItemModel.TEXTURE_FRESH;
      }
   }

   private static boolean isKirito(ItemStack stack) {
      return "KIRITO".equals(daot.compat.components.Components.get(stack, DannysAot.BLADE_SKIN));
   }

   private static boolean isNeedle(ItemStack stack) {
      return "NEEDLE".equals(daot.compat.components.Components.get(stack, DannysAot.BLADE_SKIN));
   }

   private static boolean isHyper(ItemStack stack) {
      return "HYPER".equals(daot.compat.components.Components.get(stack, DannysAot.BLADE_SKIN));
   }
}
