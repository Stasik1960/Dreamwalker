package dev.dreamwalker.bloodbornedw.tool.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.dreamwalker.bloodbornedw.composite.CompositeOverlapEdit;
import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornedw.tool.DoorLinks;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.dreamwalker.bloodbornedw.link.CompositeMechanismBridge", remap = false)
abstract class PlainDoorBridgeMixin {
    @Inject(method = "inspect", at = @At("HEAD"), cancellable = true)
    private void dw$inspect(ServerWorld world, MechanismLinks.TargetRef ref, CallbackInfoReturnable<MechanismLinks.Status> cir) {
        if (DoorLinks.eligible(ref)) cir.setReturnValue(DoorLinks.inspect(world, ref));
    }
    @ModifyExpressionValue(method = "inspect", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/architecture/RemainingArchitecture;opens(Ldev/dreamwalker/bloodbornedw/composite/CompositeRootBlock;)Z"))
    private boolean dw$otherCompositeDoors(boolean original, ServerWorld world, MechanismLinks.TargetRef ref) {
        var state = world.getBlockState(net.minecraft.util.math.BlockPos.fromLong(ref.root()));
        if (original || CompositeOverlapEdit.isDoor(state)) return true;
        if (!(state.getBlock() instanceof dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock block) || !block.spec.openable) return false;
        String name = block.spec.id.getPath();
        return name.contains("door") && !name.contains("trapdoor");
    }
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void dw$apply(ServerWorld world, MechanismLinks.TargetRef ref, boolean open, CallbackInfoReturnable<Boolean> cir) {
        if (DoorLinks.eligible(ref)) cir.setReturnValue(DoorLinks.apply(world, ref, open));
    }
}
