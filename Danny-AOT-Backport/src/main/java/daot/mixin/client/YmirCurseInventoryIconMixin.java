package daot.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import daot.DannysAot;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(AbstractInventoryScreen.class)
public abstract class YmirCurseInventoryIconMixin {
   @WrapOperation(method = "drawStatusEffectSprites", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawSprite(IIIIILnet/minecraft/client/texture/Sprite;)V"))
   private void dannysaot_swapCurseIcon(
      DrawContext guiGraphics, int x, int y, int z, int width, int height, Sprite sprite, Operation<Void> original, @Local StatusEffectInstance effect
   ) {
      if (effect.getEffectType().equals(DannysAot.CURSE_OF_YMIR_EFFECT)) {
         guiGraphics.drawItem(new ItemStack(Items.SKELETON_SKULL), x + 1, y + 1);
      } else {
         original.call(new Object[]{guiGraphics, x, y, z, width, height, sprite});
      }
   }
}
