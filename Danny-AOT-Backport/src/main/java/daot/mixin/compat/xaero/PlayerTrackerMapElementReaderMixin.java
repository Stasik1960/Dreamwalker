package daot.mixin.compat.xaero;

import daot.compat.xaero.XaeroHideHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.radar.tracker.PlayerTrackerMapElement;
import xaero.map.radar.tracker.PlayerTrackerMapElementReader;
import xaero.map.radar.tracker.PlayerTrackerMapElementRenderContext;

@Environment(EnvType.CLIENT)
@Mixin(PlayerTrackerMapElementReader.class)
public abstract class PlayerTrackerMapElementReaderMixin {
   @Inject(method = "isHidden", at = @At("HEAD"), cancellable = true, remap = false)
   private void daot$hideCloakedAndShifted(
      PlayerTrackerMapElement<?> element, PlayerTrackerMapElementRenderContext context, CallbackInfoReturnable<Boolean> cir
   ) {
      if (XaeroHideHelper.shouldHide(element.getPlayerId())) {
         cir.setReturnValue(true);
      }
   }
}
