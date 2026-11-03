package dev.dreamwalker.editorqa.mixin;

import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import dev.dreamwalker.editorqa.TouchedCells;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NativeVisuals.class, remap = false)
abstract class TouchedNativeVisualsMixin {
    @Inject(method = "stage", at = @At("HEAD"), remap = false)
    private static void dwqa$before(World world, BlockPos pos, int index, CallbackInfo ci) { TouchedCells.touch(world, pos); }
}
