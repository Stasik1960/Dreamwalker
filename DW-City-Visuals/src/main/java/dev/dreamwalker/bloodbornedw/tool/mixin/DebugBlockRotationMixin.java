package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.tool.BbToolRotations;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BbToolRotations.class, remap = false)
abstract class DebugBlockRotationMixin {
    @Redirect(method = "rotate", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/composite/GlazingTypes;step90(Lnet/minecraft/class_2680;)Lnet/minecraft/class_2680;"))
    private static BlockState dw$glazing45(BlockState state) {
        return state.with(CompositeRootBlock.ROTATION, (state.get(CompositeRootBlock.ROTATION) + 1) & 7);
    }

    @Redirect(method = "rotate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_2680;method_26186(Lnet/minecraft/class_2470;)Lnet/minecraft/class_2680;"))
    private static BlockState dw$discrete45(BlockState state, BlockRotation fallback) {
        if (state.contains(Properties.ROTATION)) return state.with(Properties.ROTATION, (state.get(Properties.ROTATION) + 2) & 15);
        return state;
    }
}
