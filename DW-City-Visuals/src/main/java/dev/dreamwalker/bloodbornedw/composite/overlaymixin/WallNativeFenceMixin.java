package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.AutoStoneFence;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeCellBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WallBlock.class)
public abstract class WallNativeFenceMixin {
    /** Vanilla shouldHavePost recognises a wall above only as a WallBlock state. */
    @ModifyVariable(method = "shouldHavePost", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private BlockState dw$wallAbove(BlockState neighbor) {
        BlockState wall = AutoStoneFence.canonicalWall(neighbor);
        return wall != null ? wall : neighbor;
    }

    /** A materialized wall's upper half-collider is not a block covering that wall. */
    @Redirect(method = "method_24422(Lnet/minecraft/class_4538;Lnet/minecraft/class_2680;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;ZZZZ)Lnet/minecraft/class_2680;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_2680;method_26220(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;)Lnet/minecraft/class_265;", remap = false),
            remap = false)
    private VoxelShape dw$coverWithoutOwnCollider(BlockState above, BlockView view, BlockPos pos) {
        if (!(above.getBlock() instanceof CompositeCellBlock)) return above.getCollisionShape(view, pos);
        BlockPos below = pos.down();
        if (!AutoStoneFence.wallLike(view.getBlockState(below))
                || !(view.getBlockEntity(below) instanceof CompositeBlockEntity wall) || wall.resident() == null) {
            return above.getCollisionShape(view, pos);
        }
        var owner = wall.resident();
        var contributions = CompositeRuntime.contributions(view, pos);
        if (contributions.stream().noneMatch(entry -> owner.equals(entry.owner()))) return above.getCollisionShape(view, pos);
        VoxelShape cover = VoxelShapes.empty();
        for (var entry : contributions) {
            if (owner.equals(entry.owner()) || view instanceof World world && DebugAdjustments.blocksDisabled(world, entry.owner())) continue;
            for (var box : CompositeRuntime.physicalParts(entry, pos)) {
                cover = VoxelShapes.union(cover, VoxelShapes.cuboid(box.minX(), box.minY(), box.minZ(),
                        box.maxX(), box.maxY(), box.maxZ()));
            }
        }
        return cover;
    }

    @Inject(method = "shouldConnectTo", at = @At("HEAD"), cancellable = true)
    private void dw$separateFence(BlockState neighbor, boolean solid, Direction side,
                                  CallbackInfoReturnable<Boolean> result) {
        if (AutoStoneFence.wallLike(neighbor)) result.setReturnValue(true);
        else if (AutoStoneFence.fenceLike(neighbor)) result.setReturnValue(false);
        else if (AutoStoneFence.knownFamily(neighbor))
            result.setReturnValue(solid && !net.minecraft.block.Block.cannotConnect(neighbor));
    }
}
