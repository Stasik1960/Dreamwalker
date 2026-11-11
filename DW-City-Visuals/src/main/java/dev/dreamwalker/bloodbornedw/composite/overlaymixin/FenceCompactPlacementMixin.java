package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.AutoStoneFence;
import dev.dreamwalker.bloodbornedw.composite.FenceCatalogue;
import dev.dreamwalker.bloodbornedw.composite.FencePlacement;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock$CompactItem", remap = false)
public abstract class FenceCompactPlacementMixin {
    @WrapMethod(method = "method_7712", remap = false)
    private ActionResult dw$placeAutomaticWall(ItemPlacementContext context, Operation<ActionResult> original) {
        ItemStack stack = context.getStack();
        if (!FenceCatalogue.automaticWallItem(stack)) return original.call(context);
        ItemStack prepared = FenceCatalogue.compact(stack);
        if (prepared == stack) return original.call(context);
        int count = prepared.getCount();
        ActionResult result = original.call(new ItemPlacementContext(context) {
            @Override public ItemStack getStack() { return prepared; }
        });
        if (result.isAccepted()) stack.decrement(Math.max(0, count - prepared.getCount()));
        return result;
    }

    @WrapOperation(method = {"method_7712", "method_7707"}, remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_1657;method_36454()F", remap = false))
    private float dw$wallUsesCardinalGrid(PlayerEntity player, Operation<Float> original, ItemPlacementContext context) {
        return FenceCatalogue.automaticWallItem(context.getStack()) ? 0.0f : original.call(player);
    }

    @WrapOperation(method = "method_7709", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_2680;method_26194(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;Lnet/minecraft/class_3726;)Lnet/minecraft/class_265;"))
    private VoxelShape dw$compactFence(BlockState state, BlockView view, BlockPos pos, ShapeContext context,
            Operation<VoxelShape> original) {
        VoxelShape shape = original.call(state, view, pos, context);
        return FencePlacement.isFence(state) ? FencePlacement.cap(shape) : shape;
    }
}
