package daot.mixin.client;

import daot.GrabbingTitan;
import daot.TitanBloodlineClientData;
import daot.TitanDashClientData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class TitanDashNoclipMixin {
   @Inject(method = "collidesWith", at = @At("HEAD"), cancellable = true)
   private void daot$noclipPureTitansWhileDashing(Entity other, CallbackInfoReturnable<Boolean> cir) {
      if (other instanceof GrabbingTitan) {
         if ((Entity)(Object)this instanceof ClientPlayerEntity) {
            if (TitanBloodlineClientData.isActive() && TitanDashClientData.isDashing()) {
               cir.setReturnValue(false);
            }
         }
      }
   }
}


