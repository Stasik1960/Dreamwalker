package daot.mixin;

import daot.VanishManager;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class VanishMixin {
   @Inject(method = "onPlayerConnect", at = @At("HEAD"))
   private void suppressVanishedJoinMessage(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
      if (VanishManager.isVanished(player.getUuid())) {
         VanishManager.suppressBroadcastCounter = 2;
      }
   }

   @Inject(method = "remove", at = @At("HEAD"))
   private void suppressVanishedLeaveMessage(ServerPlayerEntity player, CallbackInfo ci) {
      if (VanishManager.isVanished(player.getUuid())) {
         VanishManager.suppressBroadcastCounter = 1;
      }
   }

   @Inject(method = "broadcast(Lnet/minecraft/text/Text;Z)V", at = @At("HEAD"), cancellable = true)
   private void filterVanishedBroadcast(Text message, boolean overlay, CallbackInfo ci) {
      if (VanishManager.suppressBroadcastCounter > 0) {
         VanishManager.suppressBroadcastCounter--;
         ci.cancel();
      }
   }
}
