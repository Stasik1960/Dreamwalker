package daot.mixin.compat.xaero;

import daot.compat.xaero.XaeroHideHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.radar.render.element.RadarElementReader;
import xaero.hud.minimap.radar.render.element.RadarRenderContext;

@Environment(EnvType.CLIENT)
@Mixin(RadarElementReader.class)
public abstract class RadarElementReaderMixin {
   @Inject(method = "isHidden", at = @At("HEAD"), cancellable = true, remap = false)
   private void daot$hideCloakedAndShifted(Entity entity, RadarRenderContext context, CallbackInfoReturnable<Boolean> cir) {
      if (entity instanceof PlayerEntity player && XaeroHideHelper.shouldHidePlayer(player)) {
         cir.setReturnValue(true);
      }
   }
}
