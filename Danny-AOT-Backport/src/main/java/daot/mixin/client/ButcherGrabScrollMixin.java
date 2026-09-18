package daot.mixin.client;

import daot.ButcherGrabClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Mouse.class)
public class ButcherGrabScrollMixin {
   @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
   private void daot$butcherGrabScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
      if (ButcherGrabClientState.shouldInterceptScroll()) {
         if (yOffset > 0.0) {
            ButcherGrabClientState.sendScroll(1);
         } else if (yOffset < 0.0) {
            ButcherGrabClientState.sendScroll(-1);
         }

         ci.cancel();
      }
   }
}
