package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import dev.dreamwalker.bloodbornedw.composite.StudioFootprints;
import dev.dreamwalker.bloodbornedw.composite.StudioFootprints.PhysicsContext;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CompositeRuntime.class, remap = false)
public abstract class StudioCompositeMixin {
    @Unique private static final ThreadLocal<PhysicsContext> DW_STUDIO_CONTEXT = new ThreadLocal<>();

    @WrapOperation(
        method = "instance(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Ljava/util/UUID;Lnet/minecraft/class_2487;)Ldev/dreamwalker/bloodbornedw/runtime/ObjectInstance;",
        at = @At(value = "INVOKE", target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeSourceShift;footprint(Ldev/dreamwalker/bloodbornedw/composite/CompositeSpec;Lnet/minecraft/class_2680;Lnet/minecraft/class_2487;)Ljava/util/Map;"))
    private static Map<Cell, Footprint> dw$useAuthoredMembership(CompositeSpec spec, BlockState state,
            NbtCompound payload, Operation<Map<Cell, Footprint>> original) {
        if (!StudioFootprints.edited(state)) return original.call(spec, state, payload);
        var edited = StudioFootprints.dynamic(CompositeData.data(state, payload));
        return edited == null ? original.call(spec, state, payload) : edited.storage();
    }

    @WrapMethod(method = "cellShape(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;ZLnet/minecraft/class_2680;Lnet/minecraft/class_3726;)Lnet/minecraft/class_265;")
    private static VoxelShape dw$physicsContext(BlockView view, BlockPos pos, boolean collision, BlockState fallback,
            ShapeContext shape, Operation<VoxelShape> original) {
        if (!collision) return original.call(view, pos, false, fallback, shape);
        var previous = DW_STUDIO_CONTEXT.get();
        DW_STUDIO_CONTEXT.set(new PhysicsContext(view, shape instanceof EntityShapeContext entity && entity.getEntity() instanceof PlayerEntity));
        try {
            return original.call(view, pos, true, fallback, shape);
        } finally {
            if (previous == null) DW_STUDIO_CONTEXT.remove();
            else DW_STUDIO_CONTEXT.set(previous);
        }
    }

    @Inject(method = "physicalParts", at = @At("HEAD"), cancellable = true)
    private static void dw$authoredPhysical(CompositeData.Contribution contribution, BlockPos cell,
            CallbackInfoReturnable<List<Box>> result) {
        var context = DW_STUDIO_CONTEXT.get();
        if (context == null) return; // Placement continues to read the stored block-reservation shape.
        var layout = StudioFootprints.dynamic(contribution.rootData());
        if (layout == null) return;
        var offset = CompositeData.cell(cell).subtract(contribution.owner().root());
        if (context.player() && context.view() instanceof World world && DebugAdjustments.playersDisabled(world, contribution.owner())) {
            result.setReturnValue(List.of());
        } else if (context.player() && layout.players() != null) {
            result.setReturnValue(layout.players().getOrDefault(offset, List.of()));
        } else {
            result.setReturnValue(layout.stock().getOrDefault(offset, Footprint.EMPTY).collision());
        }
    }

    @Inject(method = "contextSelection", at = @At("HEAD"), cancellable = true)
    private static void dw$authoredSelection(BlockView view, CompositeData.Contribution contribution, BlockPos cell,
            ShapeContext shape, CallbackInfoReturnable<List<Box>> result) {
        var layout = StudioFootprints.dynamic(contribution.rootData());
        if (layout != null && layout.outline() != null) result.setReturnValue(contribution.shape().selection());
    }
}
