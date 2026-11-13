package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.composite.FencePlacement;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.WallBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Automatic cardinal wall states use the exact vanilla outline and movement shape. */
@Mixin(value = PrototypeWallBlock.class, remap = false)
public abstract class PrototypeFenceMixin {
    @Unique
    private BlockState dw$nativeWall(BlockState state, BlockView view, BlockPos pos) {
        if (state.get(PrototypeWallBlock.CONNECTIONS) == PrototypeWallBlock.Connections.MANUAL
                || (state.get(PrototypeWallBlock.ROTATION) & 1) != 0
                || VerticalMount.shifted(view, pos)) return null;
        BlockState nativeState = ((PrototypeWallBlock) (Object) this).nativeState(state);
        return Blocks.COBBLESTONE_WALL.getDefaultState()
                .with(PrototypeWallBlock.NORTH, nativeState.get(PrototypeWallBlock.NORTH))
                .with(PrototypeWallBlock.EAST, nativeState.get(PrototypeWallBlock.EAST))
                .with(PrototypeWallBlock.SOUTH, nativeState.get(PrototypeWallBlock.SOUTH))
                .with(PrototypeWallBlock.WEST, nativeState.get(PrototypeWallBlock.WEST))
                .with(WallBlock.UP, nativeState.get(PrototypeWallBlock.POST))
                .with(Properties.WATERLOGGED, nativeState.get(Properties.WATERLOGGED));
    }

    @Inject(method = "method_9530", at = @At("RETURN"), cancellable = true, remap = false)
    private void dw$fenceOutline(BlockState state, BlockView view, BlockPos pos, ShapeContext context,
                                 CallbackInfoReturnable<VoxelShape> cir) {
        BlockState vanilla = dw$nativeWall(state, view, pos);
        if (vanilla != null && !cir.getReturnValue().isEmpty()) cir.setReturnValue(vanilla.getOutlineShape(view, pos, context));
        else if (!VerticalMount.shifted(view, pos)) cir.setReturnValue(FencePlacement.cap(cir.getReturnValue()));
    }

    @Inject(method = "method_9549", at = @At("RETURN"), cancellable = true, remap = false)
    private void dw$fenceCollision(BlockState state, BlockView view, BlockPos pos, ShapeContext context,
                                   CallbackInfoReturnable<VoxelShape> cir) {
        BlockState vanilla = dw$nativeWall(state, view, pos);
        if (vanilla != null && !cir.getReturnValue().isEmpty()) cir.setReturnValue(vanilla.getCollisionShape(view, pos, context));
    }
}
