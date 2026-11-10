package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.tool.DoorLinks;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBlock.class)
abstract class PlainDoorRemovalMixin {
    @Inject(method = "onStateReplaced", at = @At("HEAD"))
    private void dw$removed(BlockState state, World world, BlockPos pos, BlockState next, boolean moved, CallbackInfo ci) {
        if (world instanceof ServerWorld serverWorld && DoorLinks.eligible(state)
                && state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER && !state.isOf(next.getBlock())) DoorLinks.removed(serverWorld, pos);
    }
}
