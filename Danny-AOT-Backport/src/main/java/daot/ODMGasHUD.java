package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public class ODMGasHUD {
   private static String warningMessage = null;
   private static long warningEndTime = 0L;
   private static final String LEFT_HOOK_INDICATOR = "⋘ ";
   private static final String RIGHT_HOOK_INDICATOR = " ⋙";

   public static void register() {
      HudRenderCallback.EVENT.register((HudRenderCallback)(guiGraphics, tickCounter) -> {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null && !mc.options.hudHidden) {
            PlayerEntity player = mc.player;
            ItemStack chestItem = player.getEquippedStack(EquipmentSlot.LEGS);
            if (DannysAot.isODMGear(chestItem.getItem())) {
               if (!ShifterHealthHUD.isInShifterNape()) {
                  int maxGas = DannysAot.getMaxGasForGear(chestItem);
                  int gas = DannysAot.getGasFromGear(chestItem);
                  if (player.isCreative()) {
                     gas = maxGas;
                  }

                  HookPoint leftHook = ODMTickHandler.getLeftHook(player.getUuid());
                  HookPoint rightHook = ODMTickHandler.getRightHook(player.getUuid());
                  boolean leftActive = leftHook != null && leftHook.active;
                  boolean rightActive = rightHook != null && rightHook.active;
                  long currentTime = System.currentTimeMillis();
                  if (warningMessage != null && currentTime < warningEndTime) {
                     renderWarning(guiGraphics, mc, warningMessage);
                  } else {
                     warningMessage = null;
                     renderGasBar(guiGraphics, mc, gas, maxGas, leftActive, rightActive);
                  }
               }
            }
         }
      });
   }

   public static boolean isVisible() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null || mc.options.hudHidden) {
         return false;
      } else if (ShifterHealthHUD.isInShifterNape()) {
         return false;
      } else {
         ItemStack legs = mc.player.getEquippedStack(EquipmentSlot.LEGS);
         return DannysAot.isODMGear(legs.getItem());
      }
   }

   private static void renderWarning(DrawContext g, MinecraftClient mc, String message) {
      Text text = Text.literal(message).formatted(Formatting.RED, Formatting.BOLD);
      int width = mc.textRenderer.getWidth(text);
      int x = (g.getScaledWindowWidth() - width) / 2;
      int y = FancyBar.leftBarY(mc, g);
      g.drawText(mc.textRenderer, text, x, y, 16733525, true);
   }

   private static void renderGasBar(DrawContext g, MinecraftClient mc, int gas, int maxGas, boolean leftActive, boolean rightActive) {
      float ratio = maxGas > 0 ? (float)gas / maxGas : 0.0F;
      int x = FancyBar.leftX(g);
      int y = FancyBar.leftBarY(mc, g);
      FancyBar.draw(g, x, y, 72, ratio, FancyBar.TAN);
      int iconX = x + 72 + 2;
      FancyBar.drawIcon(g, FancyBar.GAS_ICON, iconX, y, 7, 10);
      int indicatorY = y - 1;
      if (leftActive) {
         Text left = Text.literal("⋘ ").formatted(Formatting.WHITE, Formatting.BOLD);
         int lw = mc.textRenderer.getWidth(left);
         g.drawText(mc.textRenderer, left, x - 4 - lw, indicatorY, 16777215, true);
      }

      if (rightActive) {
         Text right = Text.literal(" ⋙").formatted(Formatting.WHITE, Formatting.BOLD);
         g.drawText(mc.textRenderer, right, iconX + 7 + 4, indicatorY, 16777215, true);
      }
   }

   public static void showWarning(String message, int durationMs) {
      warningMessage = message;
      warningEndTime = System.currentTimeMillis() + durationMs;
   }

   public static void showOutOfGas() {
      showWarning("Out of gas!", 1500);
   }

   public static void showNoBladeOffhand() {
      showWarning("No blade in offhand!", 1000);
   }

   public static void showNoBladeMainhand() {
      showWarning("No blade in main hand!", 1000);
   }
}
