package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.building.FenceWallMode;
import dev.dreamwalker.bloodbornedw.tool.BbToolService;
import dev.dreamwalker.bloodbornedw.tool.ToolTarget;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BbToolService.class, remap = false)
abstract class FenceWallModeMixin {
    @Inject(method = "special", at = @At("HEAD"), cancellable = true)
    private static void dreamwalker$toggleFenceWall(ServerPlayerEntity player, ToolTarget target,
            CallbackInfoReturnable<Boolean> result) {
        if (!BbToolService.canEdit(player)) return;
        Boolean changed = FenceWallMode.toggle(player, target);
        if (changed != null) result.setReturnValue(changed);
    }
}
