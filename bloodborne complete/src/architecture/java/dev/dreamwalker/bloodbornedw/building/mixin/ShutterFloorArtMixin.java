package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.building.ShutterFloorPlacement;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = CompositeRootBlock.class, remap = false)
abstract class ShutterFloorArtMixin {
    @Shadow @Final public CompositeSpec spec;

    @ModifyVariable(method = "art", at = @At("HEAD"), argsOnly = true)
    private NbtCompound dreamwalker$omitFloorCenteringFromItem(NbtCompound payload) {
        return ShutterFloorPlacement.forItem(spec, payload);
    }
}
