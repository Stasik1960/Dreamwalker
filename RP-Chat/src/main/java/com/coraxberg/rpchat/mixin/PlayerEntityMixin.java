package com.coraxberg.rpchat.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void rpchat$getName(CallbackInfoReturnable<Text> cir) {
        Entity entity = (Entity) (Object) this;
        Text customName = entity.getCustomName();
        if (customName != null) {
            cir.setReturnValue(customName);
        }
    }
}
