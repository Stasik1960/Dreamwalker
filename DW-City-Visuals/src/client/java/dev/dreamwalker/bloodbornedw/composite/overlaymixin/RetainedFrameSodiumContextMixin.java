package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import java.util.Map;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The native context mixin captures its immutable snapshot first (priority 1000). */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext", priority = 900, remap = false)
public abstract class RetainedFrameSodiumContextMixin implements RetainedFrameSodiumSnapshot {
    @Unique private Map<Integer, Integer> dw$frames = Map.of();
    @Unique private ChunkSectionPos dw$origin;
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void dw$capture(CallbackInfo ci) {
        NativeVisualSodiumSnapshot nativeSnapshot = ((NativeVisualSodiumSnapshot.Context) this).nativeVisualSnapshot();
        RetainedFrameSodiumDataAccessor access = (RetainedFrameSodiumDataAccessor)(Object)nativeSnapshot;
        NativeVisualData data = access.dw$frameSections()[13];
        if (data == null || ((RetainedFrameData)(Object)data).dw$frames().isEmpty()) return;
        int sectionY = access.dw$frameY() + 1;
        dw$frames = RetainedFrames.section(data, sectionY);
        if (!dw$frames.isEmpty()) dw$origin = ChunkSectionPos.from(access.dw$frameX() + 1, sectionY, access.dw$frameZ() + 1);
    }
    @Override public Map<Integer, Integer> dw$frameSection() { return dw$frames; }
    @Override public ChunkSectionPos dw$frameOrigin() { return dw$origin; }
}


