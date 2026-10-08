package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.architecture.BuildingTool;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ServerPlayerInteractionManager.class)
public abstract class BuilderBreakGuardMixin {
    @Shadow protected ServerPlayerEntity player;
    @Inject(method="tryBreakBlock",at=@At("HEAD"),cancellable=true)
    private void dwBuilderBreak(BlockPos pos,CallbackInfoReturnable<Boolean> ci){if(BuildingTool.isHeld(player))ci.setReturnValue(false);}
}
