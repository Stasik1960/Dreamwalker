package daot.mixin.client;

import daot.HomelanderXrayHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Mouse.class)
public class HomelanderXrayScrollMixin {
   @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
   private void daot$xrayScrollAdjust(long window, double xOffset, double yOffset, CallbackInfo ci) {
      if (HomelanderXrayHandler.shouldInterceptScroll()) {
         if (yOffset > 0.0) {
            HomelanderXrayHandler.adjustRange(1.0);
         } else if (yOffset < 0.0) {
            HomelanderXrayHandler.adjustRange(-1.0);
         }

         ci.cancel();
      }
   }
}
