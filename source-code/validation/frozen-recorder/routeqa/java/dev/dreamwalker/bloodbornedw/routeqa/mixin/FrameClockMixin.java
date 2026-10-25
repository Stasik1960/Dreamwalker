package dev.dreamwalker.bloodbornedw.routeqa.mixin;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.dreamwalker.bloodbornedw.routeqa.RouteClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Same flipFrame clock as the existing gallery probe. */
@Mixin(value=RenderSystem.class,remap=false)
public abstract class FrameClockMixin {
    @Inject(method="flipFrame",at=@At("HEAD"),remap=false)
    private static void sample(long window,CallbackInfo callback){RouteClient.presentation();}
}
