package daot.mixin;

import daot.GeassManager;
import java.util.UUID;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class MindControlAttackMixin {
   @Shadow
   public ServerPlayerEntity player;

   @Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
   private void suppressMindControlInteract(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
      if (GeassManager.isMindController(this.player.getUuid())) {
         ci.cancel();
      }
   }

   @Inject(method = "onCloseHandledScreen", at = @At("HEAD"))
   private void mirrorContainerClose(CloseHandledScreenC2SPacket packet, CallbackInfo ci) {
      if (GeassManager.isMindController(this.player.getUuid())) {
         UUID targetUUID = GeassManager.getMindControlTargetUUID();
         if (targetUUID != null) {
            ServerPlayerEntity target = this.player.server.getPlayerManager().getPlayer(targetUUID);
            if (target != null && target.currentScreenHandler != target.playerScreenHandler) {
               target.closeHandledScreen();
            }
         }
      }
   }

   @Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
   private void blockTargetContainerClick(ClickSlotC2SPacket packet, CallbackInfo ci) {
      UUID targetUUID = GeassManager.getMindControlTargetUUID();
      if (targetUUID != null && targetUUID.equals(this.player.getUuid())) {
         ci.cancel();
         this.player.currentScreenHandler.syncState();
      }
   }
}
