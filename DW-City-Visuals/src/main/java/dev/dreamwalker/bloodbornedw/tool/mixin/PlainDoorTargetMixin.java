package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornedw.tool.DoorLinks;
import dev.dreamwalker.bloodbornedw.tool.ToolTarget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ToolTarget.class, remap = false)
abstract class PlainDoorTargetMixin {
    @Inject(method = "block", at = @At("HEAD"), cancellable = true)
    private static void dw$doorTarget(ServerWorld world, BlockPos pos, PlayerEntity player, CallbackInfoReturnable<ToolTarget> cir) {
        var state = world.getBlockState(pos);
        if (DoorLinks.eligible(state) && dev.dreamwalker.bloodbornedw.tool.BbToolService.holding(player)
                && dev.dreamwalker.bloodbornedw.tool.BbToolService.canEdit(player)
                && world.canPlayerModifyAt(player, DoorLinks.root(pos, state)))
            cir.setReturnValue(DoorLinks.target(world, pos, state));
    }
    @Inject(method = "mechanism()Ldev/dreamwalker/bloodbornedw/link/MechanismLinks$TargetRef;", at = @At("HEAD"), cancellable = true)
    private void dw$doorRef(CallbackInfoReturnable<MechanismLinks.TargetRef> cir) {
        ToolTarget target = (ToolTarget)(Object)this;
        if (target.kind() == ToolTarget.Kind.BLOCK && target.uuid() != null) {
            var ref = DoorLinks.ref(target);
            if (DoorLinks.eligible(ref)) cir.setReturnValue(ref);
        }
    }
}
