package daot.mixin.client;

import daot.BloodlineClientData;
import daot.BloodlineType;
import daot.WhiteHeartTextures;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public abstract class HomelanderWhiteHeartMixin {
   @Redirect(
      method = "drawHeart",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V")
   )
   private void daot$drawHeartWhiteIfHomelander(
      DrawContext gg, Identifier texture, int x, int y, int u, int v, int w, int h
   ) {
      Identifier customTex = pickCustomHeartTexture(u);
      if (customTex == null) {
         gg.drawTexture(texture, x, y, u, v, w, h);
      } else {
         WhiteHeartTextures.register();
         gg.drawTexture(customTex, x, y, 0.0F, 0.0F, w, h, 9, 9);
      }
   }

   private static Identifier pickCustomHeartTexture(int u) {
      // In 1.20.1 the normal-heart atlas U values are 52/61 and their
      // blinking variants 70/79. Other HeartType values must retain vanilla.
      boolean isNormalFull = u == 52 || u == 70;
      boolean isNormalHalf = u == 61 || u == 79;
      if (!isNormalFull && !isNormalHalf) {
         return null;
      }
      ClientPlayerEntity self = MinecraftClient.getInstance().player;
      if (self == null || BloodlineClientData.get(self.getUuid()) != BloodlineType.HOMELANDER) {
         return null;
      }
      return isNormalHalf ? WhiteHeartTextures.HEART_HALF : WhiteHeartTextures.HEART_FULL;
   }
}
