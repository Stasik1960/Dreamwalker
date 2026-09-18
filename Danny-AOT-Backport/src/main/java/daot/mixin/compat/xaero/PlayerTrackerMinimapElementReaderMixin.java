package daot.mixin.compat.xaero;

import daot.compat.xaero.XaeroHideHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElement;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementReader;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementRenderContext;

@Environment(EnvType.CLIENT)
@Mixin(PlayerTrackerMinimapElementReader.class)
public abstract class PlayerTrackerMinimapElementReaderMixin {
   @Inject(method = "isHidden", at = @At("HEAD"), cancellable = true, remap = false)
   private void daot$hideCloakedAndShifted(
      PlayerTrackerMinimapElement<?> element, PlayerTrackerMinimapElementRenderContext context, CallbackInfoReturnable<Boolean> cir
   ) {
      if (XaeroHideHelper.shouldHide(element.getPlayerId())) {
         cir.setReturnValue(true);
      }
   }
}
