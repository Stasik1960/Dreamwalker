package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.FabricCompositeWorld;
import dev.dreamwalker.bloodbornedw.composite.FencePlacement;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FabricCompositeWorld.class, remap = false)
public abstract class FenceCompositePlacementMixin {
    @Shadow @Final public ServerWorld world;

    @Inject(method = "allowsReviewedOverlap", at = @At("RETURN"), cancellable = true)
    private void dw$constructionOverlap(ObjectInstance incoming, ObjectInstance existing, Cell cell,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (canOverlap(incoming, cell) || canOverlap(existing, cell)) cir.setReturnValue(true);
    }

    private boolean canOverlap(ObjectInstance object, Cell cell) {
        return DebugAdjustments.blocksDisabled(world, object.owner())
                || cell.y() > object.owner().root().y() && FencePlacement.isFence(CompositeData.state(object.rootData()));
    }
}
