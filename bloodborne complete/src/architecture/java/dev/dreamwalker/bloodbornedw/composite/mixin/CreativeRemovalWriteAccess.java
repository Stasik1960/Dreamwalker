package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=CompositeRuntime.class,remap=false)
public interface CreativeRemovalWriteAccess {
    @Accessor("WRITING")
    static ThreadLocal<Integer> dreamwalker$writing(){throw new AssertionError();}
}
