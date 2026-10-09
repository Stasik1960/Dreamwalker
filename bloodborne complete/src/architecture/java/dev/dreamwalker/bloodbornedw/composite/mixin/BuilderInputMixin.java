package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.architecture.BuildingTool;
import dev.dreamwalker.bloodbornedw.tool.BuilderClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(MinecraftClient.class)
public abstract class BuilderInputMixin {
    @Inject(method="doAttack",at=@At("HEAD"),cancellable=true)
    private void dwBuilderAttack(CallbackInfoReturnable<Boolean> ci){if(BuilderClient.attack())ci.setReturnValue(false);}
    @Inject(method="handleBlockBreaking",at=@At("HEAD"),cancellable=true)
    private void dwBuilderHold(boolean breaking,CallbackInfo ci){var client=MinecraftClient.getInstance();if(client.currentScreen==null&&BuildingTool.isHeld(client.player))ci.cancel();}
    @Inject(method="doItemUse",at=@At("HEAD"),cancellable=true)
    private void dwBuilderMenu(CallbackInfo ci){if(BuilderClient.use())ci.cancel();}
    @Inject(method="doItemPick",at=@At("HEAD"),cancellable=true)
    private void dwBuilderPick(CallbackInfo ci){if(BuilderClient.pick())ci.cancel();}
}
