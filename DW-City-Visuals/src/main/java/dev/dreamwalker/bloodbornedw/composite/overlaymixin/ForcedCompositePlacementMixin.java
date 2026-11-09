package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeOverlapEdit;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import dev.dreamwalker.bloodbornedw.composite.ForcedCompositePlacement;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore.ReadView;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CompositeRuntime.class, remap = false)
public abstract class ForcedCompositePlacementMixin {
    @WrapOperation(method = "placeInternal", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeRuntime;topHeight(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Ldev/dreamwalker/bloodbornedw/runtime/ObjectInstance$Owner;)Ljava/lang/Double;"))
    private static Double dw$allowMissingSupport(World world, BlockPos support, Owner excluded, Operation<Double> original,
            ServerWorld server, BlockPos pos, BlockState state, UUID id, PlayerEntity player, NbtCompound supplied) {
        Double height = original.call(world, support, excluded);
        if (height == null && NoCollidePlacement.owner(id)) return 1.0;
        return height;
    }

    @WrapOperation(method = "placeInternal", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeRuntime;instance(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Ljava/util/UUID;Lnet/minecraft/class_2487;)Ldev/dreamwalker/bloodbornedw/runtime/ObjectInstance;"))
    private static ObjectInstance dw$keepForcedObject(BlockView view, BlockPos pos, BlockState state, UUID id,
            NbtCompound payload, Operation<ObjectInstance> original, ServerWorld server, BlockPos requested,
            BlockState requestedState, UUID requestedId, PlayerEntity player, NbtCompound supplied) {
        if (NoCollidePlacement.owner(id)) return ForcedCompositePlacement.instance(server, pos, state, id, payload, player);
        return original.call(view, pos, state, id, payload);
    }

    @WrapOperation(method = "lambda$checks$8", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/architecture/SourceConversionScope;initialInstance(Ljava/util/UUID;)Z"))
    private static boolean dw$skipPhysicalPreconditions(UUID id, Operation<Boolean> original) {
        return (NoCollidePlacement.owner(id) && !CompositeOverlapEdit.inTransition()) || original.call(id);
    }

    @WrapOperation(method = "lambda$checks$8", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeSpec;requiredSupport:Z"))
    private static boolean dw$editIndependentSupport(CompositeSpec spec, Operation<Boolean> original, PlayerEntity player,
            ServerWorld world, CompositeRootBlock block, ObjectInstance incoming, ObjectInstance proposed, ReadView reads) {
        return original.call(spec) && !CompositeData.nbt(proposed.rootData().blockEntityNbt())
                .getBoolean(ForcedCompositePlacement.INDEPENDENT_SUPPORT);
    }

    @WrapOperation(method = "lambda$scheduleSupport$20", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeSpec;requiredSupport:Z"))
    private static boolean dw$retainIndependentSupport(CompositeSpec spec, Operation<Boolean> original,
            ServerWorld world, BlockPos root) {
        return original.call(spec) && !(world.getBlockEntity(root) instanceof CompositeBlockEntity entity
                && entity.payload().getBoolean(ForcedCompositePlacement.INDEPENDENT_SUPPORT));
    }
}
