package daot.mixin.client;

import daot.TitanBloodlineClientData;
import daot.TitanDashClientData;
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
public abstract class TitanDashMovementMixin {
   @Inject(method = "tickMovement", at = @At("HEAD"))
   private void daot$titanDash(CallbackInfo ci) {
      ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
      if (TitanBloodlineClientData.isActive()) {
         if (TitanDashClientData.isCharging()) {
            self.input.movementForward = 0.0F;
            self.input.movementSideways = 0.0F;
            self.input.jumping = false;
            self.input.sneaking = false;
            self.setVelocity(Vec3d.ZERO);
            self.fallDistance = 0.0F;
         } else if (TitanDashClientData.isDashing()) {
            Vec3d dir = TitanDashClientData.getDir();
            if (dir.lengthSquared() > 1.0E-4) {
               self.input.movementForward = 0.0F;
               self.input.movementSideways = 0.0F;
               self.input.jumping = false;
               self.input.sneaking = false;
               self.setVelocity(dir.normalize().multiply(5.0));
               self.fallDistance = 0.0F;
            }
         }
      }
   }
}

