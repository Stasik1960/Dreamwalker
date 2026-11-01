package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyBinding.class)
public abstract class StandardLanternAttackKeyMixin {
    @Inject(method = "setPressed", at = @At("HEAD"))
    private void dw$release(boolean pressed, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!pressed && client.options != null && (Object)this == client.options.attackKey) StandardLanternClient.releaseAttack();
    }
}
