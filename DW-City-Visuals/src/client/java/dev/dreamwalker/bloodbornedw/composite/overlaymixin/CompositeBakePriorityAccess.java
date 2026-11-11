package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh$BakeInput", remap = false)
public interface CompositeBakePriorityAccess {
    @Accessor("root") BlockPos dw$priorityRoot();
    @Accessor("state") BlockState dw$priorityState();
    @Accessor("singleSourceOwner") ObjectInstance.Owner dw$priorityOwner();
}
