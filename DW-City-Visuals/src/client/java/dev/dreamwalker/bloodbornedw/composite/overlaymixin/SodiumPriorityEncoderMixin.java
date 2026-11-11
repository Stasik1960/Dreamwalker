package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.composite.SodiumCompatibility;
import dev.dreamwalker.bloodbornedw.composite.TerrainPriorityScope;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex", remap = false)
public abstract class SodiumPriorityEncoderMixin {
    @Unique private static final boolean DW$SUPPORTED = SodiumCompatibility.verifiedClient();
    @Unique private static final int DW$STRIDE = 20;

    @WrapMethod(method = "lambda$getEncoder$0", remap = false)
    private static long dw$priority(long pointer, @Coerce Object material, @Coerce Object[] vertices,
                                    int section, Operation<Long> original) {
        long end = original.call(pointer, material, vertices, section);
        if (!DW$SUPPORTED || vertices.length != 4 || end - pointer != 4 * DW$STRIDE) return end;
        int rank = TerrainPriorityScope.rank(material);
        if (rank == 0) return end;
        // Each position word uses 30 bits, and only material bits 0–2 are defined.
        int first = (rank & 3) << 30;
        int second = (rank >>> 2 & 3) << 30;
        int third = (rank >>> 4) << 21;
        for (long vertex = pointer; vertex < end; vertex += DW$STRIDE) {
            MemoryUtil.memPutInt(vertex, (MemoryUtil.memGetInt(vertex) & 0x3fffffff) | first);
            MemoryUtil.memPutInt(vertex + 4, (MemoryUtil.memGetInt(vertex + 4) & 0x3fffffff) | second);
            MemoryUtil.memPutInt(vertex + 16, (MemoryUtil.memGetInt(vertex + 16) & ~(7 << 21)) | third);
        }
        return end;
    }
}
