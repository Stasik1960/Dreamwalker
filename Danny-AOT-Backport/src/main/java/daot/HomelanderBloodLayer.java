package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class HomelanderBloodLayer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
   private static final Identifier[] STAGE_TEXTURES = new Identifier[]{tex("v_blood_1"), tex("v_blood_2"), tex("v_blood_3"), tex("v_blood_4")};

   private static Identifier tex(String name) {
      return new Identifier("dannys-aot", "textures/skin/blood/" + name + ".png");
   }

   public HomelanderBloodLayer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> renderer) {
      super(renderer);
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
         HomelanderBloodTracker.BloodState state = HomelanderBloodTracker.getClientState(player.getUuid());
         if (state != null) {
            int stage = state.stage();
            if (stage >= 1 && stage <= STAGE_TEXTURES.length) {
               Identifier tex = STAGE_TEXTURES[stage - 1];
               if (tex != null) {
                  float alpha = this.computeAlpha(state);
                  if (!(alpha <= 0.001F)) {
                     VertexConsumer vc = bufferSource.getBuffer(RenderLayer.getEntityTranslucent(tex));
                     int alphaByte = MathHelper.clamp((int)(alpha * 255.0F), 0, 255);
                     int color = alphaByte << 24 | 16777215;
                     this.getContextModel().render(poseStack, vc, packedLight, OverlayTexture.DEFAULT_UV, daot.compat.RenderColors.red(color), daot.compat.RenderColors.green(color), daot.compat.RenderColors.blue(color), daot.compat.RenderColors.alpha(color));
                  }
               }
            }
         }
      }
   }

   private float computeAlpha(HomelanderBloodTracker.BloodState state) {
      long fadeStarted = state.fadeStartedAtGameTime();
      if (fadeStarted < 0L) {
         return 1.0F;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world == null) {
            return 0.0F;
         } else {
            int fadeDuration = state.fadeDurationTicks();
            if (fadeDuration <= 0) {
               fadeDuration = 600;
            }

            long fadeElapsed = mc.world.getTime() - fadeStarted;
            if (fadeElapsed <= 0L) {
               return 1.0F;
            } else {
               return fadeElapsed >= fadeDuration ? 0.0F : MathHelper.clamp(1.0F - (float)fadeElapsed / fadeDuration, 0.0F, 1.0F);
            }
         }
      }
   }
}
