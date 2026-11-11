package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.composite.TerrainPriorityScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex", remap = false)
public abstract class SodiumPriorityEncoderMixin {
    @WrapMethod(method = "lambda$getEncoder$0", remap = false)
    private static long dw$priority(long pointer, @Coerce Object material, @Coerce Object[] vertices,
                                    int section, Operation<Long> original) {
        float offset = TerrainPriorityScope.offset(material);
        if (offset == 0 || vertices.length != 4) return original.call(pointer, material, vertices, section);
        PriorityVertexAccess a = (PriorityVertexAccess) vertices[0], b = (PriorityVertexAccess) vertices[1];
        PriorityVertexAccess c = (PriorityVertexAccess) vertices[2], d = (PriorityVertexAccess) vertices[3];
        float ax = c.dw$x() - a.dw$x(), ay = c.dw$y() - a.dw$y(), az = c.dw$z() - a.dw$z();
        float bx = d.dw$x() - b.dw$x(), by = d.dw$y() - b.dw$y(), bz = d.dw$z() - b.dw$z();
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float length = nx * nx + ny * ny + nz * nz;
        if (!(length > 1e-12F) || !Float.isFinite(length)) return original.call(pointer, material, vertices, section);
        float scale = offset / (float) Math.sqrt(length);
        float dx = nx * scale, dy = ny * scale, dz = nz * scale;
        float[] saved = TerrainPriorityScope.vertexScratch();
        for (int i = 0; i < 4; i++) {
            PriorityVertexAccess v = (PriorityVertexAccess) vertices[i];
            int at = 3 * i;
            saved[at] = v.dw$x();
            saved[at + 1] = v.dw$y();
            saved[at + 2] = v.dw$z();
            if (!dw$encodable(saved[at] + dx) || !dw$encodable(saved[at + 1] + dy)
                    || !dw$encodable(saved[at + 2] + dz)) return original.call(pointer, material, vertices, section);
        }
        try {
            for (int i = 0; i < 4; i++) {
                PriorityVertexAccess v = (PriorityVertexAccess) vertices[i];
                v.dw$x(saved[3 * i] + dx);
                v.dw$y(saved[3 * i + 1] + dy);
                v.dw$z(saved[3 * i + 2] + dz);
            }
            return original.call(pointer, material, vertices, section);
        } finally {
            for (int i = 0; i < 4; i++) {
                PriorityVertexAccess v = (PriorityVertexAccess) vertices[i];
                v.dw$x(saved[3 * i]);
                v.dw$y(saved[3 * i + 1]);
                v.dw$z(saved[3 * i + 2]);
            }
        }
    }

    @Unique
    private static boolean dw$encodable(float position) {
        return position >= -8 && position < 24;
    }
}