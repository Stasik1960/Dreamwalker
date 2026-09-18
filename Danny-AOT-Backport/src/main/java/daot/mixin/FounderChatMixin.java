package daot.mixin;

import daot.GeassManager;
import daot.ModCommands;
import daot.VanishManager;
import java.util.UUID;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerManager.class, priority = 900)
public abstract class FounderChatMixin {
   @Inject(
      method = "broadcast(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void handleFounderChat(SignedMessage message, ServerPlayerEntity sender, Parameters bound, CallbackInfo ci) {
      if (GeassManager.hasSelectedTarget(sender.getUuid()) && GeassManager.tryParseCommand(sender, message.getSignedContent(), sender.server)) {
         ci.cancel();
      } else {
         if (GeassManager.isMindController(sender.getUuid())) {
            UUID targetUUID = GeassManager.getMindControlTargetUUID();
            if (targetUUID != null) {
               ServerPlayerEntity target = sender.server.getPlayerManager().getPlayer(targetUUID);
               if (target != null) {
                  Text chatMessage = Text.translatable("chat.type.text", target.getDisplayName(), Text.literal(message.getSignedContent()));
                  sender.server.getPlayerManager().broadcast(chatMessage, false);
                  ci.cancel();
                  return;
               }
            }
         }

         if (!ModCommands.isFounderChatActive(sender.getUuid())) {
            if (VanishManager.isVanished(sender.getUuid())) {
               ci.cancel();
               Text chatMessage = Text.translatable("chat.type.text", sender.getDisplayName(), Text.literal(message.getSignedContent()));
               sender.server.getPlayerManager().broadcast(chatMessage, false);
            } else {
               for (ServerPlayerEntity founderPlayer : sender.server.getPlayerManager().getPlayerList()) {
                  if (ModCommands.isFounderChatActive(founderPlayer.getUuid()) && founderPlayer != sender) {
                     String rawText = message.getSignedContent();
                     MutableText relayMessage = Text.literal("")
                        .append(Text.literal("<" + sender.getName().getString() + "> ").formatted(Formatting.GRAY))
                        .append(Text.literal(rawText));
                     founderPlayer.sendMessage(relayMessage);
                  }
               }
            }
         } else {
            String rawText = message.getSignedContent();
            MutableText founderName = Text.literal("Founder Danny").styled(style -> style.withColor(11184351).withBold(true));
            MutableText fullMessage = Text.literal("")
               .append(Text.literal("<").styled(style -> style.withColor(11184351)))
               .append(founderName)
               .append(Text.literal("> ").styled(style -> style.withColor(11184351)))
               .append(Text.literal(rawText));

            for (ServerPlayerEntity player : sender.server.getPlayerManager().getPlayerList()) {
               player.sendMessage(fullMessage);
            }

            ci.cancel();
         }
      }
   }
}
