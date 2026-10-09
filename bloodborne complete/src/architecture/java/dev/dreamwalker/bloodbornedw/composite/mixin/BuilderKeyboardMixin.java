package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.tool.BuilderClient;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native press capture keeps short remappable editor taps reliable at a low frame rate. */
@Mixin(Keyboard.class)
public abstract class BuilderKeyboardMixin {
    @Inject(method="onKey",at=@At("HEAD"))
    private void dwBuilderKey(long window,int key,int scanCode,int action,int modifiers,CallbackInfo ci){
        BuilderClient.keyboardEvent(window,key,scanCode,action,modifiers);
    }
}
