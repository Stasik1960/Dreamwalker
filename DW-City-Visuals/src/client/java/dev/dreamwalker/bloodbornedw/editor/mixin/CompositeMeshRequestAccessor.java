package dev.dreamwalker.bloodbornedw.editor.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.CompositeMeshJobs$Request", remap = false)
public interface CompositeMeshRequestAccessor {
    @Accessor("world") ClientWorld dw$world();
    @Accessor("owner") CompositeBlockEntity dw$owner();
    @Accessor("state") BlockState dw$state();
}
