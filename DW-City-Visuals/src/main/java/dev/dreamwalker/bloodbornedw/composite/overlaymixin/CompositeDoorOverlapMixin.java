package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeOverlapEdit;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Result;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CompositeRuntime.class, remap = false)
abstract class CompositeDoorOverlapMixin {
    @Shadow
    private static Result transitionInternal(ServerWorld world, Owner owner, BlockState state, PlayerEntity actor) {
        throw new AssertionError();
    }

    @ModifyVariable(method = "placeInternal", at = @At("HEAD"), argsOnly = true)
    private static NbtCompound dw$rememberPlacementMode(NbtCompound payload, ServerWorld world, BlockPos pos,
                                                        BlockState state, UUID id, PlayerEntity actor, NbtCompound original) {
        if (!CompositeOverlapEdit.isDoor(state)) return payload;
        String key = CompositeOverlapEdit.OPEN_WITH_OVERLAP;
        if (NoCollidePlacement.ignoringBlocks() && NoCollidePlacement.armed(actor)) {
            NbtCompound marked = payload == null ? new NbtCompound() : payload.copy();
            marked.putBoolean(key, true);
            return marked;
        }
        if (payload != null && payload.contains(key)) {
            NbtCompound ordinary = payload.copy();
            ordinary.remove(key);
            return ordinary;
        }
        return payload;
    }

    @Redirect(method = "transition(Lnet/minecraft/class_3218;Ldev/dreamwalker/bloodbornedw/runtime/ObjectInstance$Owner;Lnet/minecraft/class_2680;Lnet/minecraft/class_1657;)Ldev/dreamwalker/bloodbornedw/runtime/TransactionCore$Result;",
            at = @At(value = "INVOKE", target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeRuntime;transitionInternal(Lnet/minecraft/class_3218;Ldev/dreamwalker/bloodbornedw/runtime/ObjectInstance$Owner;Lnet/minecraft/class_2680;Lnet/minecraft/class_1657;)Ldev/dreamwalker/bloodbornedw/runtime/TransactionCore$Result;"))
    private static Result dw$openThroughBlocks(ServerWorld world, Owner owner, BlockState state, PlayerEntity actor) {
        BlockPos pos = CompositeData.pos(owner.root());
        if (CompositeOverlapEdit.isDoor(state) && state.get(CompositeRootBlock.OPEN) && world.isChunkLoaded(pos)
                && world.getBlockEntity(pos) instanceof CompositeBlockEntity root && owner.equals(root.resident())
                && world.getBlockState(pos).isOf(state.getBlock()) && !world.getBlockState(pos).get(CompositeRootBlock.OPEN)
                && root.payload().getBoolean(CompositeOverlapEdit.OPEN_WITH_OVERLAP)) {
            return CompositeOverlapEdit.run(owner, () -> transitionInternal(world, owner, state, actor));
        }
        return transitionInternal(world, owner, state, actor);
    }
}
