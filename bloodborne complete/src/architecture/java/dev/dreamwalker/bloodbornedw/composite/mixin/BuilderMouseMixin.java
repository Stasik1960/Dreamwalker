package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.tool.BuilderClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Editor wheel gestures are consumed before the vanilla hotbar changes. Screen scrolling is untouched. */
@Mixin(Mouse.class)
public abstract class BuilderMouseMixin {
    @Inject(method="onMouseButton",at=@At("HEAD"))
    private void dwBuilderButton(long window,int button,int action,int modifiers,CallbackInfo ci){
        BuilderClient.mouseEvent(window,button,action,modifiers);
    }
    @Inject(method="onMouseScroll",at=@At("HEAD"),cancellable=true)
    private void dwBuilderScroll(long window,double horizontal,double vertical,CallbackInfo ci){
        if(BuilderClient.scroll(window,horizontal,vertical))ci.cancel();
    }
}
