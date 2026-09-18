package daot;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class FreezeVignetteHUD {
   private static final Identifier VIGNETTE_TEXTURE = new Identifier("textures/misc/vignette.png");
   private static final float PULSE_SPEED = (float) (Math.PI / 20);
   private static final float FADE_SPEED = 0.02F;
   private static float currentAlpha = 0.0F;

   public static void register() {
      HudRenderCallback.EVENT.register((HudRenderCallback)(guiGraphics, tickCounter) -> {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null && !mc.options.hudHidden) {
            boolean shouldShow = FreezeVignetteClientData.isFrozen();
            if (shouldShow) {
               currentAlpha = Math.min(1.0F, currentAlpha + 0.02F);
            } else {
               currentAlpha = Math.max(0.0F, currentAlpha - 0.02F);
            }

            if (!(currentAlpha <= 0.001F)) {
               int screenW = mc.getWindow().getScaledWidth();
               int screenH = mc.getWindow().getScaledHeight();
               float time = (float)(System.currentTimeMillis() % 10000L) / 50.0F;
               float pulse = (float)(0.275 + 0.1 * Math.sin(time * (float) (Math.PI / 20)));
               float finalAlpha = pulse * currentAlpha;
               RenderSystem.enableBlend();
               RenderSystem.defaultBlendFunc();
               RenderSystem.disableDepthTest();
               RenderSystem.setShader(GameRenderer::getPositionTexProgram);
               RenderSystem.setShaderTexture(0, VIGNETTE_TEXTURE);
               RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, finalAlpha);
               Tessellator tesselator = Tessellator.getInstance();
               BufferBuilder buffer = tesselator.getBuffer();
      buffer.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
               buffer.vertex(0.0F, screenH, -90.0F).texture(0.0F, 1.0F).next();
               buffer.vertex(screenW, screenH, -90.0F).texture(1.0F, 1.0F).next();
               buffer.vertex(screenW, 0.0F, -90.0F).texture(1.0F, 0.0F).next();
               buffer.vertex(0.0F, 0.0F, -90.0F).texture(0.0F, 0.0F).next();
               BufferRenderer.drawWithGlobalProgram(buffer.end());
               RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
               RenderSystem.enableDepthTest();
            }
         }
      });
   }
}
