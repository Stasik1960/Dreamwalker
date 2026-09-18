package daot.mixin.client;

import daot.CombatModeState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Mouse.class)
public class CombatModeMouseMixin {
   @Shadow
   private double cursorDeltaX;
   @Shadow
   private double cursorDeltaY;

   @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
   private void dannysaot$combatModeTurnPlayer(CallbackInfo ci) {
      if (CombatModeState.isEnabled()) {
         if (!CombatModeState.isShiftLockEnabled()) {
            if (!CombatModeState.isRightMouseHeld()) {
               CombatModeState.moveCursor(this.cursorDeltaX, this.cursorDeltaY);
               this.cursorDeltaX = 0.0;
               this.cursorDeltaY = 0.0;
               ci.cancel();
            }
         }
      }
   }

   @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
   private void dannysaot$combatModeScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
      if (CombatModeState.isEnabled()) {
         if (yOffset > 0.0) {
            CombatModeState.adjustZoom(1);
         } else if (yOffset < 0.0) {
            CombatModeState.adjustZoom(-1);
         }

         ci.cancel();
      }
   }
}
