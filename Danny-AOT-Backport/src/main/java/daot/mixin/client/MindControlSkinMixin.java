package daot.mixin.client;

import daot.GeassClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(AbstractClientPlayerEntity.class)
public class MindControlSkinMixin {
   @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
   private void swapSkinForMindControl(CallbackInfoReturnable<Identifier> cir) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && GeassClientState.isMindControlController) {
         if ((Object)this == mc.player) {
            int targetId = GeassClientState.mindControlTargetEntityId;
            if (targetId != -1) {
               if (mc.player.getWorld().getEntityById(targetId) instanceof AbstractClientPlayerEntity targetPlayer) {
                  cir.setReturnValue(targetPlayer.getSkinTexture());
               }
            }
         }
      }
   }
}


