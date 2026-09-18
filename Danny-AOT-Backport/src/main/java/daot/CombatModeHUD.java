package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class CombatModeHUD {
   private static final int CROSSHAIR_COLOR = -855638017;
   private static final int CROSSHAIR_SIZE = 6;
   private static final int CROSSHAIR_GAP = 2;
   private static final int LABEL_COLOR = -2130706433;

   public static void register() {
      HudRenderCallback.EVENT.register((HudRenderCallback)(guiGraphics, tickCounter) -> {
         if (CombatModeState.isEnabled()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && !mc.options.hudHidden) {
               int centerX = guiGraphics.getScaledWindowWidth() / 2;
               int centerY = guiGraphics.getScaledWindowHeight() / 2;
               if (!CombatModeState.isShiftLockEnabled()) {
                  int cx = centerX + (int)CombatModeState.getCursorScreenX();
                  int cy = centerY + (int)CombatModeState.getCursorScreenY();
                  guiGraphics.fill(cx, cy - 6, cx + 1, cy - 2, -855638017);
                  guiGraphics.fill(cx, cy + 2 + 1, cx + 1, cy + 6 + 1, -855638017);
                  guiGraphics.fill(cx - 6, cy, cx - 2, cy + 1, -855638017);
                  guiGraphics.fill(cx + 2 + 1, cy, cx + 6 + 1, cy + 1, -855638017);
               }

               String label = CombatModeState.isShiftLockEnabled() ? "COMBAT [SHIFT LOCK]" : "COMBAT";
               int labelWidth = mc.textRenderer.getWidth(label);
               guiGraphics.drawText(mc.textRenderer, label, centerX - labelWidth / 2, 4, -2130706433, false);
            }
         }
      });
   }
}
