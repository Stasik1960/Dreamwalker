package daot.mixin;

import daot.GeassManager;
import daot.VanishManager;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class MindControlPushMixin {
   @Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
   private void preventMindControlPick(CallbackInfoReturnable<Boolean> cir) {
      if ((Object)this instanceof ServerPlayerEntity sp) {
         if (GeassManager.isMindController(sp.getUuid())) {
            cir.setReturnValue(false);
            return;
         }

         if (VanishManager.isVanished(sp.getUuid())) {
            cir.setReturnValue(false);
         }
      }
   }

   @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
   private void preventMindControlPush(CallbackInfoReturnable<Boolean> cir) {
      if ((Object)this instanceof ServerPlayerEntity sp) {
         if (GeassManager.isMindController(sp.getUuid())) {
            cir.setReturnValue(false);
            return;
         }

         UUID targetUUID = GeassManager.getMindControlTargetUUID();
         if (targetUUID != null && targetUUID.equals(sp.getUuid())) {
            cir.setReturnValue(false);
            return;
         }

         if (VanishManager.isVanished(sp.getUuid())) {
            cir.setReturnValue(false);
         }
      }
   }
}

