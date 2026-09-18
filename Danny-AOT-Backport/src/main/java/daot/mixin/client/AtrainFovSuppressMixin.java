package daot.mixin.client;

import daot.AtrainSpeedClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AtrainFovSuppressMixin {
   @Inject(method = "getFovMultiplier", at = @At("HEAD"), cancellable = true)
   private void daot$suppressAtrainSpeedFov(CallbackInfoReturnable<Float> cir) {
      AbstractClientPlayerEntity self = (AbstractClientPlayerEntity)(Object)this;
      if (AtrainSpeedClientState.isActive(self.getUuid())) {
         cir.setReturnValue(1.0F);
      }
   }
}

