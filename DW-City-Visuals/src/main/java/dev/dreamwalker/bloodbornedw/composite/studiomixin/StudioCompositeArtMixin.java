package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec.Part;
import dev.dreamwalker.bloodbornedw.composite.ImportedStairLegacyArt;
import dev.dreamwalker.bloodbornedw.composite.StudioLayouts;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ImportedStairLegacyArt.class, remap = false)
public abstract class StudioCompositeArtMixin {
    @ModifyReturnValue(method = "selectedParts", at = @At("RETURN"))
    private static List<Part> dw$translateArt(List<Part> parts, CompositeRootBlock block, BlockState state, NbtCompound payload) {
        var model = StudioLayouts.model(block.spec);
        return model == null ? parts : StudioLayouts.art(parts, model, StudioLayouts.pose(model, state), false);
    }
}
