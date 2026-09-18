package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class ShifterStaminaHUD {
   private static final int CONTROLS_KEY_COLOR = 16766720;
   private static final int CONTROLS_LABEL_COLOR = 13421772;
   private static final int HIGHLIGHT_WHITE = 16777215;
   private static final int HIGHLIGHT_RED = 16724787;
   private static final int HIGHLIGHT_GREEN = 5635925;
   private static float clientStamina = 0.0F;
   private static float clientMaxStamina = 1000.0F;
   private static boolean isShifter = false;
   private static boolean isBeast = false;
   private static boolean isFounding = false;
   private static float smoothStamina = -1.0F;
   private static float smoothY = Float.NaN;
   private static final int AWAKEN_HIGHLIGHT_TICKS = 120;
   private static final int COMMAND_HIGHLIGHT_TICKS = 40;
   private static final int DEFAULT_HIGHLIGHT_TICKS = 10;
   private static final int COOLDOWN_RED_TICKS = 10;
   private static final int COMMAND_COOLDOWN_TICKS = 40;
   private static int highlightAwaken = 0;
   private static int highlightStop = 0;
   private static int highlightContinue = 0;
   private static int highlightRegroup = 0;
   private static int highlightTarget = 0;
   private static boolean awakenIsRed = false;
   private static boolean stopIsRed = false;
   private static boolean continueIsRed = false;
   private static boolean regroupIsRed = false;
   private static boolean targetIsRed = false;
   private static String targetMode = "none";
   private static boolean awakenToggleActive = false;
   private static long lastStopTick = -9999L;
   private static long lastContinueTick = -9999L;
   private static boolean hudHidden = false;

   public static void setTargetMode(String mode) {
      targetMode = mode;
   }

   public static String getTargetMode() {
      return targetMode;
   }

   public static void setAwakenToggleActive(boolean active) {
      awakenToggleActive = active;
   }

   public static boolean isAwakenToggleActive() {
      return awakenToggleActive;
   }

   public static void toggleHidden() {
      hudHidden = !hudHidden;
   }

   public static boolean isHidden() {
      return hudHidden;
   }

   public static boolean isShifter() {
      return isShifter;
   }

   public static boolean isFounding() {
      return isFounding;
   }

   public static boolean isVisible() {
      return isShifter && !hudHidden;
   }

   public static void register() {
      HudRenderCallback.EVENT.register((HudRenderCallback)(guiGraphics, tickCounter) -> {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null && !mc.options.hudHidden) {
            if (isShifter && !hudHidden) {
               renderStaminaBar(guiGraphics, mc);
            }
         }
      });
   }

   public static void updateStamina(float stamina, float maxStamina, boolean hasBeast, boolean hasFounding) {
      clientStamina = stamina;
      clientMaxStamina = maxStamina;
      isShifter = true;
      isBeast = hasBeast;
      isFounding = hasFounding;
   }

   public static void clearStamina() {
      isShifter = false;
      isBeast = false;
      isFounding = false;
      clientStamina = 0.0F;
      smoothStamina = -1.0F;
      awakenToggleActive = false;
   }

   private static long getCurrentTick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.world != null ? mc.world.getTime() : 0L;
   }

   public static void highlightAbility(int index) {
      long tick = getCurrentTick();
      switch (index) {
         case 0:
            highlightAwaken = 120;
            awakenIsRed = false;
            break;
         case 1:
            if (tick - lastStopTick < 40L) {
               highlightStop = 10;
               stopIsRed = true;
            } else {
               highlightStop = 40;
               stopIsRed = false;
               lastStopTick = tick;
            }
            break;
         case 2:
            if (tick - lastContinueTick < 40L) {
               highlightContinue = 10;
               continueIsRed = true;
            } else {
               highlightContinue = 40;
               continueIsRed = false;
               lastContinueTick = tick;
            }
            break;
         case 3:
            highlightRegroup = 10;
            regroupIsRed = false;
            break;
         case 4:
            highlightTarget = 10;
            targetIsRed = false;
      }
   }

   public static boolean isBeastControlsVisible() {
      return isShifter && isBeast && BloodlineClientData.get() == BloodlineType.ROYAL;
   }

   public static void tick() {
      if (highlightAwaken > 0) {
         highlightAwaken--;
      }

      if (highlightStop > 0) {
         highlightStop--;
      }

      if (highlightContinue > 0) {
         highlightContinue--;
      }

      if (highlightRegroup > 0) {
         highlightRegroup--;
      }

      if (highlightTarget > 0) {
         highlightTarget--;
      }
   }

   private static String getKeyName(KeyBinding key) {
      return key.getBoundKeyLocalizedText().getString().toUpperCase();
   }

   private static void renderStaminaBar(DrawContext g, MinecraftClient mc) {
      int targetY = FancyBar.rightBarY(mc, g);
      if (Float.isNaN(smoothY)) {
         smoothY = targetY;
      }

      smoothY = smoothY + (targetY - smoothY) * 0.15F;
      int y = Math.round(smoothY);
      if (smoothStamina < 0.0F) {
         smoothStamina = clientStamina;
      }

      smoothStamina = smoothStamina + (clientStamina - smoothStamina) * 0.25F;
      float ratio = clientMaxStamina > 0.0F ? smoothStamina / clientMaxStamina : 0.0F;
      boolean isDanger = ratio < 0.1F;
      boolean red = awakenToggleActive || isDanger;
      int x = FancyBar.rightX(g, 72);
      FancyBar.draw(g, x, y, 72, ratio, red ? FancyBar.RED : FancyBar.GOLD);
      FancyBar.drawIcon(g, FancyBar.BOLT, x - 2 - 7, y, 7, 10);
   }

   private static void appendControl(MutableText component, KeyBinding key, String label, boolean first, int highlightTicks, boolean isRed) {
      if (!first) {
         component.append(Text.literal("  ").fillStyle(Style.EMPTY.withColor(13421772)));
      }

      if (highlightTicks > 0) {
         int color = isRed ? 16724787 : 16777215;
         component.append(Text.literal("(" + getKeyName(key) + ")").fillStyle(Style.EMPTY.withColor(color).withBold(true)));
         component.append(Text.literal(" " + label).fillStyle(Style.EMPTY.withColor(color).withBold(true)));
      } else {
         component.append(Text.literal("(" + getKeyName(key) + ")").fillStyle(Style.EMPTY.withColor(16766720).withBold(true)));
         component.append(Text.literal(" " + label).fillStyle(Style.EMPTY.withColor(13421772)));
      }
   }
}
