package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeShapeSnapshots;
import dev.dreamwalker.bloodbornedw.composite.SurfacePriority;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies priority to the recorded vertex metadata before the mesh can be shared or uploaded. */
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh", remap = false)
public abstract class CompositeMeshPriorityMixin {
    @Inject(method = "bakePrepared", at = @At("RETURN"), remap = false)
    private static void dw$prioritizePrepared(@Coerce Object raw, @Coerce Object rawView,
                                              CallbackInfoReturnable<Object> result) {
        if (!(result.getReturnValue() instanceof CompositeRecordedMeshAccess mesh)) return;
        var input = (CompositeBakePriorityAccess) raw;
        var view = (BlockRenderView) rawView;
        var root = input.dw$priorityRoot();
        var state = input.dw$priorityState();
        if (!(state.getBlock() instanceof CompositeRootBlock block)) return;
        var owner = input.dw$priorityOwner();
        if (owner == null) {
            var resident = CompositeShapeSnapshots.owner(view, root);
            if (resident != null && CompositeData.pos(resident.root()).equals(root)) owner = resident;
        }
        SurfacePriority.composite(mesh.dw$priorityLights(), block, root, state, owner);
    }

    @Inject(method = "bake", at = @At("RETURN"), remap = false)
    private static void dw$prioritizeSynchronous(MinecraftClient client, CompositeBlockEntity owner,
                                                 CompositeRootBlock block, BlockState state, NbtCompound payload,
                                                 int light, Map<Long, Long> dependencies,
                                                 CallbackInfoReturnable<Object> result) {
        if (result.getReturnValue() instanceof CompositeRecordedMeshAccess mesh) {
            SurfacePriority.composite(mesh.dw$priorityLights(), block, owner.getPos(), state, owner.resident());
        }
    }
}
