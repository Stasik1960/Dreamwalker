package daot.mixin.client;

import daot.BloodlineClientData;
import daot.BloodlineType;
import daot.SoldierboyPlayerAnimationHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class SoldierboyMovementLockMixin {
   @Inject(method = "tickMovement", at = @At("HEAD"))
   private void daot$soldierboyLock(CallbackInfo ci) {
      ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
      if (BloodlineClientData.get(self.getUuid()) == BloodlineType.SOLDIERBOY) {
         if (SoldierboyPlayerAnimationHandler.isInChestAbilityState(self.getUuid())) {
            self.input.movementForward = 0.0F;
            self.input.movementSideways = 0.0F;
            self.input.jumping = false;
            self.input.sneaking = false;
            self.setVelocity(Vec3d.ZERO);
            self.fallDistance = 0.0F;
         }
      }
   }
}

