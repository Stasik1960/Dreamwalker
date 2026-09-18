package daot.mixin.client;

import daot.DannysAot;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class LocalPlayerCrouchMixin {
   @Inject(method = "isInSneakingPose", at = @At("RETURN"), cancellable = true)
   private void preventAirborneCrouchVisual(CallbackInfoReturnable<Boolean> cir) {
      if ((Boolean)cir.getReturnValue()) {
         ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
         if (!self.isOnGround() && DannysAot.isODMGear(self.getEquippedStack(EquipmentSlot.LEGS).getItem())) {
            cir.setReturnValue(false);
         }
      }
   }
}

