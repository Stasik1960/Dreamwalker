package daot.mixin.client;

import daot.HomelanderGrabClientHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public class HomelanderGrabbedMovementMixin {
   @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
   private void daot$grabbedSuppressTravel(Vec3d travelVector, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         if ((Object)this == mc.player) {
            if (HomelanderGrabClientHandler.findGrabberOf(mc.player.getId()) != null) {
               LivingEntity self = (LivingEntity)(Object)this;
               self.setVelocity(Vec3d.ZERO);
               self.fallDistance = 0.0F;
               ci.cancel();
            }
         }
      }
   }
}

