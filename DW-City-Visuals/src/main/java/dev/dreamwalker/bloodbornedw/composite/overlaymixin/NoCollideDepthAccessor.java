package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = NoCollidePlacement.class, remap = false)
public interface NoCollideDepthAccessor {
    @Accessor("DEPTH")
    static ThreadLocal<Integer> dw$depth() {
        throw new AssertionError();
    }
}
