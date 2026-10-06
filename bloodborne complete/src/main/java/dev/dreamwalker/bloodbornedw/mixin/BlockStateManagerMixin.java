package dev.dreamwalker.bloodbornedw.mixin;

import dev.dreamwalker.bloodbornedw.block.DwBlocks;
import dev.dreamwalker.bloodbornedw.block.DwCarrierState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.State;
import net.minecraft.state.StateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;

@Mixin(Block.class)
abstract class BlockStateManagerMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/state/StateManager$Builder;build(Ljava/util/function/Function;Lnet/minecraft/state/StateManager$Factory;)Lnet/minecraft/state/StateManager;"))
    private StateManager<Block, BlockState> bloodborneDw$addVisual(StateManager.Builder<Block, BlockState> builder, Function<Block, BlockState> owner, StateManager.Factory<Block, BlockState> factory) {
        if (DwCarrierState.isBuilding()) builder.add(DwBlocks.VISUAL);
        return builder.build(owner, factory);
    }
}
