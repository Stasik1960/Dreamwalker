package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh$Mesh", remap = false)
public interface CompositeRecordedMeshAccess {
    @Accessor("lights") int[] dw$priorityLights();
}
