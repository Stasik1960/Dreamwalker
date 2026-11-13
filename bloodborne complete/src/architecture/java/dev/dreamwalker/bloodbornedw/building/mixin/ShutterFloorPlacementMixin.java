package dev.dreamwalker.bloodbornedw.building.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.dreamwalker.bloodbornedw.building.ShutterFloorPlacement;
import dev.dreamwalker.bloodbornedw.composite.CompositeItem;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Result;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CompositeItem.class, remap = false)
abstract class ShutterFloorPlacementMixin {
    @WrapOperation(method = "lambda$placeInternal$1", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeRuntime;place(Lnet/minecraft/class_3218;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Ljava/util/UUID;Lnet/minecraft/class_1657;Lnet/minecraft/class_2487;)Ldev/dreamwalker/bloodbornedw/runtime/TransactionCore$Result;"))
    private static Result dreamwalker$placeShutterOneCellCloser(ServerWorld world, BlockPos pos, BlockState state,
            UUID id, PlayerEntity player, NbtCompound payload, Operation<Result> original,
            @Local(argsOnly = true) ItemPlacementContext context) {
        if (context.getSide() == Direction.UP && state.getBlock() instanceof CompositeRootBlock block
                && block.spec.id.getPath().equals(ShutterFloorPlacement.WINDOW_PATH)) {
            pos = pos.offset(context.getHorizontalPlayerFacing().getOpposite());
            payload = ShutterFloorPlacement.markFloorMount(payload);
        }
        return original.call(world, pos, state, id, player, payload);
    }
}
