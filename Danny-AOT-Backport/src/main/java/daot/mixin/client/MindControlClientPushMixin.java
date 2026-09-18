package daot.mixin.client;

import daot.GeassClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public class MindControlClientPushMixin {
   @Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
   private void preventMindControlPickClient(CallbackInfoReturnable<Boolean> cir) {
      if (GeassClientState.isMindControlController) {
         MinecraftClient mc = MinecraftClient.getInstance();
         Entity self = (Entity)(Object)this;
         if (self == mc.player) {
            cir.setReturnValue(false);
         } else {
            int targetId = GeassClientState.mindControlTargetEntityId;
            if (targetId != -1 && self.getId() == targetId) {
               cir.setReturnValue(false);
            }
         }
      }
   }

   @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
   private void preventMindControlPushClient(CallbackInfoReturnable<Boolean> cir) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         Entity self = (Entity)(Object)this;
         if (GeassClientState.isMindControlController) {
            if (self == mc.player) {
               cir.setReturnValue(false);
               return;
            }

            int targetId = GeassClientState.mindControlTargetEntityId;
            if (targetId != -1 && self.getId() == targetId) {
               cir.setReturnValue(false);
               return;
            }
         }

         if (GeassClientState.isMindControlTarget && self == mc.player) {
            cir.setReturnValue(false);
         }
      }
   }
}

