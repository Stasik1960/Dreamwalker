package daot.mixin;

import daot.ModCommands;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SentMessage;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class FounderMsgSpyMixin {
   @Inject(method = "sendChatMessage(Lnet/minecraft/network/message/SentMessage;ZLnet/minecraft/network/message/MessageType$Parameters;)V", at = @At("HEAD"))
   private void relayPrivateMessageToFounder(SentMessage message, boolean filtered, Parameters boundChatType, CallbackInfo ci) {
      ServerPlayerEntity target = (ServerPlayerEntity)(Object)this;
      MessageType incoming = target.getWorld().getRegistryManager()
         .get(RegistryKeys.MESSAGE_TYPE).get(MessageType.MSG_COMMAND_INCOMING);
      if (boundChatType.type().equals(incoming)) {
         Text senderName = boundChatType.name();
         Text targetName = boundChatType.targetName() != null ? boundChatType.targetName() : target.getDisplayName();
         String senderNameStr = senderName.getString();
         MutableText spyLine = Text.literal("")
            .append(Text.literal("[MSG] ").formatted(Formatting.LIGHT_PURPLE))
            .append(Text.empty().append(senderName).formatted(Formatting.GRAY))
            .append(Text.literal(" -> ").formatted(Formatting.DARK_GRAY))
            .append(Text.empty().append(targetName).formatted(Formatting.GRAY))
            .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
            .append(message.getContent());

         for (ServerPlayerEntity founder : target.server.getPlayerManager().getPlayerList()) {
            if (ModCommands.isFounderChatActive(founder.getUuid()) && founder != target && !founder.getDisplayName().getString().equals(senderNameStr)) {
               founder.sendMessage(spyLine);
            }
         }
      }
   }
}

