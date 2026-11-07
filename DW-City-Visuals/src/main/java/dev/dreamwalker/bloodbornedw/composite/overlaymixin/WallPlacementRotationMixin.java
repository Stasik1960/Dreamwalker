package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallItem;
import dev.dreamwalker.bloodbornedw.composite.PlacementWallArt;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PrototypeWallItem.class, remap = false)
public abstract class WallPlacementRotationMixin {
    @WrapMethod(method = "method_7712(Lnet/minecraft/class_1750;)Lnet/minecraft/class_1269;", remap = false)
    private ActionResult dw$placementAngle(ItemPlacementContext context, Operation<ActionResult> original) {
        ItemStack stack = context.getStack();
        ItemStack working = PlacementWallArt.prepare(context);
        if (working == null) return ActionResult.FAIL;
        int count = working.getCount();
        ItemPlacementContext prepared = new ItemPlacementContext(context) {
            @Override public ItemStack getStack() { return working; }
        };
        ActionResult result = original.call(prepared);
        if (result.isAccepted()) stack.decrement(Math.max(0, count - working.getCount()));
        return result;
    }

    @WrapOperation(method = "method_7707", at = @At(value = "INVOKE",
        target = "Ldev/dreamwalker/bloodbornedw/architecture/wall/PrototypeWallBlock;reconnect(Lnet/minecraft/class_2680;Lnet/minecraft/class_1936;Lnet/minecraft/class_2338;)Lnet/minecraft/class_2680;"))
    private BlockState dw$perpendicularAutoWall(PrototypeWallBlock wall, BlockState state, WorldAccess world, BlockPos pos,
            Operation<BlockState> original) {
        int rotation = (state.get(PrototypeWallBlock.ROTATION) + 2) & 7;
        return original.call(wall, state.with(PrototypeWallBlock.ROTATION, rotation), world, pos);
    }
}
