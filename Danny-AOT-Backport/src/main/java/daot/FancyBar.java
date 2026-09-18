package daot;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

@Environment(EnvType.CLIENT)
final class FancyBar {
   private static final int XP_TEX_W = 182;
   private static final int XP_TEX_H = 5;
   static final int HEIGHT = 5;
   static final int ROW_LEN = 81;
   static final int ICON_GAP = 2;
   static final Identifier BOLT = sprite("hud/shifter_bolt");
   static final int ICON_W = 7;
   static final int ICON_H = 10;
   static final Identifier GAS_ICON = sprite("hud/gashudicon");
   static final int GAS_ICON_W = 7;
   static final int GAS_ICON_H = 10;
   static final int HEART_ICON_W = 10;
   static final int HEART_ICON_H = 10;
   static final int ICON_BAR_W = 72;
   static final int GAS_BAR_W = 72;
   static final int HEART_BAR_W = 69;
   static final int AWAKENED_W = 60;
   private static final Identifier XP_BACKGROUND = sprite("hud/experience_bar_background");
   static final Identifier GOLD = sprite("hud/xp_bar_gold");
   static final Identifier TAN = sprite("hud/xp_bar_tan");
   static final Identifier RED = sprite("hud/xp_bar_red");
   static final Identifier GREY = sprite("hud/xp_bar_grey");
   static final Identifier GREEN = sprite("hud/xp_bar_green");
   private static final int SLOT_STEP = 10;
   private static final boolean THIRST_ROW = FabricLoader.getInstance().isModLoaded("legendarysurvivaloverhaul")
      || FabricLoader.getInstance().isModLoaded("survivaloverhaul");

   private FancyBar() {
   }

   private static Identifier sprite(String path) {
      return new Identifier("dannys-aot", "textures/gui/sprites/" + path + ".png");
   }

   static int leftBarY(MinecraftClient mc, DrawContext g) {
      return barRowY(mc, g, leftRows(mc));
   }

   static int rightBarY(MinecraftClient mc, DrawContext g) {
      return barRowY(mc, g, rightRows(mc));
   }

   static int centerBarY(MinecraftClient mc, DrawContext g, int slot) {
      return barRowY(mc, g, Math.max(leftRows(mc), rightRows(mc))) - slot * 10;
   }

   private static int leftRows(MinecraftClient mc) {
      return hasArmor(mc) ? 1 : 0;
   }

   private static int rightRows(MinecraftClient mc) {
      int rows = 0;
      if (hasAir(mc)) {
         rows++;
      }

      if (THIRST_ROW) {
         rows++;
      }

      return rows;
   }

   private static int barRowY(MinecraftClient mc, DrawContext g, int extraRows) {
      int bottom = g.getScaledWindowHeight();
      if (ShifterAbilityHUD.isAbilityBarVisible()) {
         return bottom - 45;
      } else {
         GameMode gt = mc.interactionManager != null ? mc.interactionManager.getCurrentGameMode() : null;
         boolean survival = gt == GameMode.SURVIVAL || gt == GameMode.ADVENTURE;
         return !survival ? bottom - 31 : bottom - 47 - extraRows * 10;
      }
   }

   private static boolean hasArmor(MinecraftClient mc) {
      return mc.player != null && mc.player.getArmor() > 0;
   }

   private static boolean hasAir(MinecraftClient mc) {
      return mc.player == null ? false : mc.player.isSubmergedIn(FluidTags.WATER) || mc.player.getAir() < mc.player.getMaxAir();
   }

   static void drawIcon(DrawContext g, Identifier icon, int x, int barY, int w, int h) {
      RenderSystem.enableBlend();
      g.drawTexture(icon, x, barY + (5 - h) / 2, 0, 0, w, h, w, h);
      RenderSystem.disableBlend();
   }

   static void drawTextureIcon(DrawContext g, Identifier tex, int x, int barY, int w, int h) {
      RenderSystem.enableBlend();
      g.drawTexture(tex, x, barY + (5 - h) / 2, w, h, 0.0F, 0.0F, w, h, w, h);
      RenderSystem.disableBlend();
   }

   static int leftX(DrawContext g) {
      return g.getScaledWindowWidth() / 2 - 91;
   }

   static int rightX(DrawContext g, int width) {
      return g.getScaledWindowWidth() / 2 + 91 - width;
   }

   static int centeredX(DrawContext g, int width) {
      return (g.getScaledWindowWidth() - width) / 2;
   }

   static void draw(DrawContext g, int x, int y, int width, float ratio, Identifier progress) {
      int leftSeg = width / 2;
      int rightU = 182 - (width - leftSeg);
      RenderSystem.enableBlend();
      g.drawTexture(XP_BACKGROUND, x, y, 0, 0, leftSeg, 5, 182, 5);
      g.drawTexture(XP_BACKGROUND, x + leftSeg, y, rightU, 0, width - leftSeg, 5, 182, 5);
      ratio = Math.max(0.0F, Math.min(1.0F, ratio));
      int fillW = Math.round(width * ratio);
      if (fillW > 0) {
         int leftW = Math.min(fillW, leftSeg);
         g.drawTexture(progress, x, y, 0, 0, leftW, 5, 182, 5);
         if (fillW > leftSeg) {
            int rightW = fillW - leftSeg;
            g.drawTexture(progress, x + leftSeg, y, rightU, 0, rightW, 5, 182, 5);
         }
      }

      RenderSystem.disableBlend();
   }
}
