package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.tool.BuilderClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserves normal wheel/hotbar behaviour unless a documented modifier is held. */
@Mixin(Mouse.class)
public abstract class BuilderMouseMixin {
    @Inject(method="onMouseButton",at=@At("HEAD"))
    private void dwBuilderButton(long window,int button,int action,int modifiers,CallbackInfo ci){BuilderClient.inputEvent(window,net.minecraft.client.util.InputUtil.Type.MOUSE.createFromCode(button),action,modifiers);}
    @Inject(method="onMouseScroll",at=@At("HEAD"),cancellable=true)
    private void dwBuilderScroll(long window,double horizontal,double vertical,CallbackInfo ci){if(BuilderClient.scroll(vertical))ci.cancel();}
}
