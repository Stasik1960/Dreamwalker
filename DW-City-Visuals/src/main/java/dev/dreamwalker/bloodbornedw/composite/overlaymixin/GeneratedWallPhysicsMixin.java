package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import dev.dreamwalker.bloodbornedw.composite.FenceCatalogue;
import dev.dreamwalker.bloodbornedw.composite.ImportedArtworkBlock;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Let newly generated poses use their own geometry instead of the old import table's pose zero. */
@Mixin(value = CompositeSpec.class, remap = false)
abstract class GeneratedWallPhysicsMixin {
    private static final String FOOTPRINT = "footprint(Lnet/minecraft/class_2680;D)Ljava/util/Map;";
    private static final String PHYSICAL = "simplePhysicalFootprint(Lnet/minecraft/class_2680;DDDD)Ljava/util/Map;";
    private static final String SPEC = "(Ldev/dreamwalker/bloodbornedw/composite/CompositeSpec;)Z";

    @WrapOperation(method = {FOOTPRINT, PHYSICAL}, remap = false, at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/composite/ImportedCollisionV126;supports" + SPEC, remap = false))
    private boolean dw$legacyWallCells(CompositeSpec spec, Operation<Boolean> original, BlockState state) {
        return original.call(spec) && !FenceCatalogue.generatedWallPose(spec.id.getPath(), ImportedArtworkBlock.variant(state));
    }

    @WrapOperation(method = {FOOTPRINT, PHYSICAL}, remap = false, at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/composite/ImportedCollisionV125;supports" + SPEC, remap = false))
    private boolean dw$legacyWallVolumes(CompositeSpec spec, Operation<Boolean> original, BlockState state) {
        return original.call(spec) && !FenceCatalogue.generatedWallPose(spec.id.getPath(), ImportedArtworkBlock.variant(state));
    }
}
