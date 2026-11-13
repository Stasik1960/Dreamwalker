package dev.dreamwalker.bloodbornedw.composite.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.NativeVisuals", remap = false)
public interface CreativeRemovalNativeVisualAccess {
    @Invoker(value = "index", remap = false)
    static int dreamwalker$index(BlockView world, BlockPos pos) {
        throw new AssertionError("Untransformed accessor");
    }

    @Invoker(value = "stage", remap = false)
    static void dreamwalker$stage(World world, BlockPos pos, int visual) {
        throw new AssertionError("Untransformed accessor");
    }

    @Invoker(value = "publish", remap = false)
    static void dreamwalker$publish(World world, BlockPos pos) {
        throw new AssertionError("Untransformed accessor");
    }

    @Invoker(value = "write", remap = false)
    static boolean dreamwalker$write(World world, BlockPos pos, int visual, int flags) {
        throw new AssertionError("Untransformed accessor");
    }
}
