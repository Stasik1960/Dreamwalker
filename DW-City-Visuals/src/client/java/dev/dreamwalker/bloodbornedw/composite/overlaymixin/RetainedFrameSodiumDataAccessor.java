package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access the same immutable section array already captured by the native renderer. */
@Mixin(value = NativeVisualSodiumSnapshot.class, remap = false)
public interface RetainedFrameSodiumDataAccessor {
    @Accessor("sections") NativeVisualData[] dw$frameSections();
    @Accessor("x") int dw$frameX();
    @Accessor("y") int dw$frameY();
    @Accessor("z") int dw$frameZ();
}


