package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class ShifterMusclesLayer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/shifter_muscles.png");
   private ShifterMusclesArmorRenderer renderer;
   private ItemStack musclesStack;

   public ShifterMusclesLayer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> renderer) {
      super(renderer);
   }

   private ShifterMusclesArmorRenderer getRenderer() {
      if (this.renderer == null) {
         this.renderer = new ShifterMusclesArmorRenderer();
      }

      return this.renderer;
   }

   private ItemStack getMusclesStack() {
      if (this.musclesStack == null) {
         this.musclesStack = new ItemStack(DannysAot.SHIFTER_MUSCLES);
      }

      return this.musclesStack;
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
         if (player.getVehicle() instanceof ShifterTitan st && st.isDismounting()) {
            ShifterMusclesArmorRenderer r = this.getRenderer();
            r.prepForRender(player, this.getMusclesStack(), EquipmentSlot.CHEST, this.getContextModel());
            VertexConsumer buffer = bufferSource.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
            r.render(poseStack, buffer, packedLight, OverlayTexture.DEFAULT_UV, daot.compat.RenderColors.red(-1), daot.compat.RenderColors.green(-1), daot.compat.RenderColors.blue(-1), daot.compat.RenderColors.alpha(-1));
         }
      }
   }
}
