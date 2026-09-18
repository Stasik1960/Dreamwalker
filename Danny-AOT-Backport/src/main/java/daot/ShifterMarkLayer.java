package daot;

import java.util.HashMap;
import java.util.Map;
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
public class ShifterMarkLayer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
   private static final Map<String, Identifier> MARK_TEXTURES = new HashMap<>();

   private static Identifier tex(String name) {
      return new Identifier("dannys-aot", "textures/skin/shifter_marks/" + name + ".png");
   }

   public ShifterMarkLayer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> renderer) {
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
         ShifterMarkTracker.MarkState state = ShifterMarkTracker.getClientState(player.getUuid());
         if (state != null) {
            Identifier tex = MARK_TEXTURES.get(state.markType());
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

   private float computeAlpha(ShifterMarkTracker.MarkState state) {
      long fadeStarted = state.fadeStartedAtGameTime();
      if (fadeStarted < 0L) {
         return 1.0F;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world == null) {
            return 0.0F;
         } else {
            long fadeElapsed = mc.world.getTime() - fadeStarted;
            if (fadeElapsed <= 0L) {
               return 1.0F;
            } else {
               return fadeElapsed >= 600L ? 0.0F : MathHelper.clamp(1.0F - (float)fadeElapsed / 600.0F, 0.0F, 1.0F);
            }
         }
      }
   }

   static {
      MARK_TEXTURES.put("attack", tex("titanmarkattack"));
      MARK_TEXTURES.put("armored", tex("titanmarkarmored"));
      MARK_TEXTURES.put("beast", tex("titanmarkbeast"));
      MARK_TEXTURES.put("colossal", tex("titanmarkcolossal"));
      MARK_TEXTURES.put("female", tex("titanmarkfemale"));
      MARK_TEXTURES.put("warhammer", tex("titanmarkwarhammer"));
      MARK_TEXTURES.put("jaw", tex("titanmarkjaw"));
   }
}
