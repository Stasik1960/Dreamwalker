package daot.mixin.client;

import daot.PushAuraClientData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ItemEntity.class)
public class ItemEntitySpinMixin {
   @Inject(method = "getRotation(F)F", at = @At("RETURN"), cancellable = true)
   private void dannysaot_slowSpinInFreezeZone(float partialTick, CallbackInfoReturnable<Float> cir) {
      ItemEntity self = (ItemEntity)(Object)this;
      double factor = PushAuraClientData.getAnimFreezeFactor(self);
      if (factor < 1.0) {
         cir.setReturnValue(cir.getReturnValueF() * (float)factor);
      }
   }
}

