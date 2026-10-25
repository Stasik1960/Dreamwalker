package dev.dreamwalker.bloodbornedw.routeqa.mixin;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Measurement-only: retain a slow baseline instead of losing its recording
 * to a thirty-second network timeout. Identical for both sides and both JARs. */
@Mixin(ClientConnection.class)
public abstract class RouteTimeoutMixin {
 @Inject(method="channelActive",at=@At("RETURN"),remap=false)
 private void routeTimeout(ChannelHandlerContext context,CallbackInfo ci){
  if(System.getProperty("dw.route.output")!=null&&context.pipeline().get("timeout") instanceof ReadTimeoutHandler)
   context.pipeline().replace("timeout","timeout",new ReadTimeoutHandler(300));
 }
}
