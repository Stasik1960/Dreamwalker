package dev.dreamwalker.bloodbornedw.composite;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.render.model.BakedQuad;
import org.joml.Matrix4f;

/** Keeps the first opaque quad when a mesh emits two coincident faces of the same winding. */
public final class CoplanarQuads {
    private static final int UNITS = 32768;
    private static final int LIMIT = 1 << 20;
    private static final long MASK = (1L << 21) - 1;
    private static final long OUTSIDE = -1L;
    private static final int MAX_TRACKED = 4096;

    private final Map<Face, BakedQuad> emitted = new HashMap<>();

    private record Face(long first, long second, long third, long fourth) {}

    public boolean skip(Matrix4f matrix, BakedQuad quad) {
        int[] vertices = quad.getVertexData();
        int stride = vertices.length / 4;
        long a = vertex(matrix, vertices, 0);
        long b = vertex(matrix, vertices, stride);
        long c = vertex(matrix, vertices, stride * 2);
        long d = vertex(matrix, vertices, stride * 3);
        if ((a | b | c | d) < 0 || a == b || a == c || a == d || b == c || b == d || c == d) return false;
        // Match cyclic vertex order. The reverse winding describes the other face.
        Face face;
        if (a < b && a < c && a < d) face = new Face(a, b, c, d);
        else if (b < c && b < d) face = new Face(b, c, d, a);
        else if (c < d) face = new Face(c, d, a, b);
        else face = new Face(d, a, b, c);
        BakedQuad first = emitted.get(face);
        if (first == null) {
            if (emitted.size() < MAX_TRACKED) emitted.put(face, quad);
            return false;
        }
        // Retain all cutout/translucent faces, including glass and authored overlays.
        if (!StaticArchitectureGeometry.opaqueQuad(quad)) return false;
        if (StaticArchitectureGeometry.opaqueQuad(first)) return true;
        emitted.put(face, quad);
        return false;
    }

    private static long vertex(Matrix4f matrix, int[] data, int offset) {
        float x = Float.intBitsToFloat(data[offset]);
        float y = Float.intBitsToFloat(data[offset + 1]);
        float z = Float.intBitsToFloat(data[offset + 2]);
        int px = Math.round(UNITS * (matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30()));
        int py = Math.round(UNITS * (matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31()));
        int pz = Math.round(UNITS * (matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + matrix.m32()));
        if (px < -LIMIT || px >= LIMIT || py < -LIMIT || py >= LIMIT || pz < -LIMIT || pz >= LIMIT) return OUTSIDE;
        return ((px & MASK) << 42) | ((py & MASK) << 21) | (pz & MASK);
    }
}
