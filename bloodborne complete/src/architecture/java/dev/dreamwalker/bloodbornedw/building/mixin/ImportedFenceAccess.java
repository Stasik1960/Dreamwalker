package dev.dreamwalker.bloodbornedw.building.mixin;

import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.AutoStoneFence", remap = false)
public interface ImportedFenceAccess {
    @Invoker(value = "matches", remap = false)
    static boolean dreamwalker$matches(BlockState state) {
        throw new AssertionError("Untransformed accessor");
    }

    @Invoker(value = "wallLike", remap = false)
    static boolean dreamwalker$wallLike(BlockState state) {
        throw new AssertionError("Untransformed accessor");
    }

    @Invoker(value = "fenceLike", remap = false)
    static boolean dreamwalker$fenceLike(BlockState state) {
        throw new AssertionError("Untransformed accessor");
    }
}
