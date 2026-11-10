package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.link.ObjectPolicies;
import dev.dreamwalker.bloodbornedw.tool.DoorLinks;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DoorBlock.class)
abstract class PlainDoorUseMixin {
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void dw$manual(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
            BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient && DoorLinks.eligible(state) && DoorBlock.canOpenByHand(state)) {
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer && DoorLinks.eligible(state) && DoorBlock.canOpenByHand(state)) {
            var target = DoorLinks.existing(serverWorld, pos, state);
            if (target != null && !ObjectPolicies.allowManual(serverPlayer, DoorLinks.ref(target))) cir.setReturnValue(ActionResult.CONSUME);
        }
    }

    // The client does not predict the door state. Include the opening player in
    // the server sound so the same action is audible once to everyone nearby.
    @ModifyArg(method = "onUse", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/block/DoorBlock;playOpenCloseSound(Lnet/minecraft/entity/Entity;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Z)V"),
        index = 0)
    private Entity dw$playSoundForOpener(Entity actor) {
        DoorBlock door = (DoorBlock) (Object) this;
        return DoorLinks.eligible(door.getDefaultState()) ? null : actor;
    }
}
