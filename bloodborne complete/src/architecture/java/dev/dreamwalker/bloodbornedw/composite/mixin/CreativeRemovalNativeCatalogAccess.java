package dev.dreamwalker.bloodbornedw.composite.mixin;

import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.NativeGridCatalog", remap = false)
public interface CreativeRemovalNativeCatalogAccess {
    @Invoker(value = "state", remap = false)
    static BlockState dreamwalker$state(int visual) {
        throw new AssertionError("Untransformed accessor");
    }
}
