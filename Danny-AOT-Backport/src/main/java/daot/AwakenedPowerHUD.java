package daot;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class AwakenedPowerHUD {
   private static final Identifier VIGNETTE_TEXTURE = new Identifier("textures/misc/vignette.png");
   private static final float PULSE_SPEED = (float) (Math.PI / 20);

   public static void register() {
      HudRenderCallback.EVENT.register((HudRenderCallback)(guiGraphics, tickCounter) -> {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null && !mc.options.hudHidden) {
            if (AwakenedPowerClientData.isActive()) {
               renderVignette(guiGraphics, mc, 0.4F, 0.0F, 0.0F);
            } else if (mc.player.getVehicle() instanceof ArmoredTitanEntity ctAr && ctAr.isConsciousnessTransferActive()) {
               renderVignette(guiGraphics, mc, 0.85F, 0.6F, 0.0F);
            } else if (isRidingLowHealthShifter(mc.player.getVehicle()) || isDefeatedExhausted(mc.player)) {
               renderVignette(guiGraphics, mc, 0.4F, 0.0F, 0.0F);
            }

            if (TitanBloodlineClientData.isActive()) {
               renderTitanDashBar(guiGraphics, mc);
            } else if (BloodlineClientData.isAckerman()) {
               renderBar(guiGraphics, mc);
            }
         }
      });
   }

   private static void renderTitanDashBar(DrawContext g, MinecraftClient mc) {
      float charge = TitanDashClientData.getCharge();
      float maxCharge = TitanDashClientData.getMaxCharge();
      float ratio = maxCharge > 0.0F ? charge / maxCharge : 0.0F;
      boolean ready = ratio >= 0.999F;
      int x = FancyBar.centeredX(g, 60);
      int y = FancyBar.centerBarY(mc, g, slotIndex());
      FancyBar.draw(g, x, y, 60, ratio, ready ? FancyBar.RED : FancyBar.GREY);
   }

   private static void renderVignette(DrawContext guiGraphics, MinecraftClient mc, float r, float g, float b) {
      int screenW = mc.getWindow().getScaledWidth();
      int screenH = mc.getWindow().getScaledHeight();
      float time = (float)(System.currentTimeMillis() % 10000L) / 50.0F;
      float pulse = (float)(0.275 + 0.1 * Math.sin(time * (float) (Math.PI / 20)));
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableDepthTest();
      RenderSystem.setShader(GameRenderer::getPositionTexProgram);
      RenderSystem.setShaderTexture(0, VIGNETTE_TEXTURE);
      RenderSystem.setShaderColor(r, g, b, pulse);
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

   private static boolean isRidingLowHealthShifter(Entity vehicle) {
      if (!(vehicle instanceof LivingEntity titan)) {
         return false;
      } else {
         boolean isShifter = vehicle instanceof AttackTitanEntity
            || vehicle instanceof ArmoredTitanEntity
            || vehicle instanceof FemaleTitanEntity
            || vehicle instanceof ColossalTitanEntity
            || vehicle instanceof BeastTitanEntity
            || vehicle instanceof WarhammerTitanEntity;
         if (!isShifter) {
            return false;
         } else {
            float max = titan.getMaxHealth();
            return max > 0.0F && titan.getHealth() > 1.5F && titan.getHealth() < max * 0.3F;
         }
      }
   }

   private static boolean isDefeatedExhausted(PlayerEntity player) {
      StatusEffectInstance weakness = player.getStatusEffect(StatusEffects.WEAKNESS);
      return weakness != null && weakness.getAmplifier() >= 4;
   }

   private static int slotIndex() {
      return !ShifterStaminaHUD.isVisible() && !ODMGasHUD.isVisible() ? 0 : 1;
   }

   private static void renderBar(DrawContext g, MinecraftClient mc) {
      float charge = AwakenedPowerClientData.getCharge();
      float maxCharge = AwakenedPowerClientData.getMaxCharge();
      boolean active = AwakenedPowerClientData.isActive();
      float ratio = maxCharge > 0.0F ? charge / maxCharge : 0.0F;
      int x = FancyBar.centeredX(g, 60);
      int y = FancyBar.centerBarY(mc, g, slotIndex());
      FancyBar.draw(g, x, y, 60, ratio, active ? FancyBar.RED : FancyBar.GREY);
   }
}
