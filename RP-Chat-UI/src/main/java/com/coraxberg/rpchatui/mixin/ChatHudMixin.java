package com.coraxberg.rpchatui.mixin;

import com.coraxberg.rpchatui.client.ChatUiState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void rpchatui$render(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (ChatUiState.shouldRenderInHud()) {
            ChatUiState.render(context, currentTick, mouseX, mouseY);
        }
        ci.cancel();
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), require = 1)
    private void rpchatui$addDecoratedMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        ChatUiState.addMessage(message);
    }
}
