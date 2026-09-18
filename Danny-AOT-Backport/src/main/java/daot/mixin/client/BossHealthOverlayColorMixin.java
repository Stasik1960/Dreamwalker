package daot.mixin.client;

import daot.CustomBossBarColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(BossBarHud.class)
public class BossHealthOverlayColorMixin {
   @Inject(method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;)V", at = @At("HEAD"))
   private void daot$tintCustomBar(DrawContext guiGraphics, int x, int y, BossBar bossEvent, CallbackInfo ci) {
      int rgb = CustomBossBarColors.get(bossEvent.getName());
      if (rgb >= 0) {
         float r = (rgb >> 16 & 0xFF) / 255.0F;
         float g = (rgb >> 8 & 0xFF) / 255.0F;
         float b = (rgb & 0xFF) / 255.0F;
         guiGraphics.setShaderColor(r, g, b, 1.0F);
      }
   }

   @Inject(method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;)V", at = @At("RETURN"))
   private void daot$resetTint(DrawContext guiGraphics, int x, int y, BossBar bossEvent, CallbackInfo ci) {
      if (CustomBossBarColors.get(bossEvent.getName()) >= 0) {
         guiGraphics.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }
   }
}
