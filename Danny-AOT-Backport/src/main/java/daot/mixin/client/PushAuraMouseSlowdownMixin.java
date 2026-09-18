package daot.mixin.client;

import daot.PushAuraClientData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Mouse.class)
public class PushAuraMouseSlowdownMixin {
   @Shadow
   private double cursorDeltaX;
   @Shadow
   private double cursorDeltaY;

   @Inject(method = "updateMouse", at = @At("HEAD"))
   private void dannysaot$slowCameraTurn(CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         int auraId = PushAuraClientData.pushAuraEntityId;
         if (auraId != -1 && mc.player.getId() != auraId) {
            double factor = PushAuraClientData.getAnimFreezeFactor(mc.player);
            if (factor < 1.0) {
               this.cursorDeltaX *= factor;
               this.cursorDeltaY *= factor;
            }
         }
      }
   }
}
