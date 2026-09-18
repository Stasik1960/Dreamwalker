package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;

@Environment(EnvType.CLIENT)
public final class ShifterHealthHUD {
   private ShifterHealthHUD() {
   }

   public static LivingEntity riddenShifterInNape() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         return null;
      } else {
         return mc.player.getVehicle() instanceof ShifterTitan st && !st.isDismounting() && mc.player.getVehicle() instanceof LivingEntity titan ? titan : null;
      }
   }

   public static boolean isInShifterNape() {
      return riddenShifterInNape() != null;
   }

   public static void register() {
      HudRenderCallback.EVENT.register((HudRenderCallback)(g, tickCounter) -> {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null && !mc.options.hudHidden) {
            LivingEntity titan = riddenShifterInNape();
            if (titan != null) {
               float ratio = titan.getMaxHealth() > 0.0F ? titan.getHealth() / titan.getMaxHealth() : 0.0F;
               int x = FancyBar.leftX(g);
               int y = FancyBar.leftBarY(mc, g);
               FancyBar.draw(g, x, y, 81, ratio, FancyBar.GREEN);
            }
         }
      });
   }
}
