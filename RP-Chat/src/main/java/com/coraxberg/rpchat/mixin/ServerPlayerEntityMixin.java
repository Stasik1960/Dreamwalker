package com.coraxberg.rpchat.mixin;

import com.coraxberg.rpchat.RpChatMod;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
    private void rpchat$getPlayerListName(CallbackInfoReturnable<Text> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        cir.setReturnValue(RpChatMod.displayNameText(player));
    }
}
