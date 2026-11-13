package dev.dreamwalker.bloodbornedw.composite.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.NativeGridBridge", remap = false)
public interface CreativeRemovalNativeBridgeAccess {
    @Invoker(value = "base", remap = false)
    static int dreamwalker$base(BlockView world, BlockPos pos) {
        throw new AssertionError("Untransformed accessor");
    }

    @Invoker(value = "validate", remap = false)
    static void dreamwalker$validate(int visual, BlockState carrier) {
        throw new AssertionError("Untransformed accessor");
    }
}
