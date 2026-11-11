package dev.dreamwalker.bloodbornedw.composite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.model.BakedQuad;
import org.joml.Matrix4f;

/** Subtracts earlier opaque faces from later faces on the same side of a plane during a mesh build. */
public final class CoplanarQuads {
    private static final double PLANE_EPSILON = 0.00001;
    private static final double AREA_EPSILON = 0.0000000001;
    private static final int MAX_TRACKED = 4096;
    private static final int MAX_PER_BUCKET = 64;
    private static final int MAX_PIECES = 32;

    private final Map<PlaneKey, Face> planes = new HashMap<>();
    private final double[] coordinates = new double[12];
    private int tracked;


    private record PlaneKey(int axis, int winding, long slopeU, long slopeV, long offset) {}
    private static final class Face {
        final PlaneKey key;
        final double[] points;
        final int size;
        final double slopeU, slopeV, offset;
        final double minU, maxU, minV, maxV;
        final BakedQuad opacitySource;
        Face previous;
        int bucketSize = 1;

        Face(PlaneKey key, double[] points, int size, double slopeU, double slopeV, double offset,
             double minU, double maxU, double minV, double maxV, BakedQuad opacitySource) {
            this.key = key;
            this.points = points;
            this.size = size;
            this.slopeU = slopeU;
            this.slopeV = slopeV;
            this.offset = offset;
            this.minU = minU;
            this.maxU = maxU;
            this.minV = minV;
            this.maxV = maxV;
            this.opacitySource = opacitySource;
        }

        double u(int i) { return points[i * 3]; }
        double v(int i) { return points[i * 3 + 1]; }
        double depth(int i) { return points[i * 3 + 2]; }

        boolean overlaps(Face other) {
            return minU < other.maxU && maxU > other.minU && minV < other.maxV && maxV > other.minV
                    && liesOn(other) && other.liesOn(this);
        }

        boolean sameBoundary(Face other) {
            if (size != other.size || minU != other.minU || maxU != other.maxU
                    || minV != other.minV || maxV != other.maxV) return false;
            for (int start = 0; start < size; start++) {
                if (!samePoint(0, other, start)) continue;
                for (int i = 1; i < size; i++) {
                    if (!samePoint(i, other, (start + i) % size)) return false;
                }
                return true;
            }
            return false;
        }

        private boolean samePoint(int i, Face other, int j) {
            return u(i) == other.u(j) && v(i) == other.v(j);
        }

        private boolean liesOn(Face other) {
            for (int i = 0; i < size; i++) {
                double distance = depth(i) - (other.offset + other.slopeU * u(i) + other.slopeV * v(i));
                if (Math.abs(distance) > PLANE_EPSILON) return false;
            }
            return true;
        }
    }

    // Weights stay relative to the parent quad through all clipping operations.
    private record Vertex(double u, double v, double a, double b, double c, double d) {
        Vertex interpolate(Vertex other, double t) {
            return new Vertex(mix(u, other.u, t), mix(v, other.v, t), mix(a, other.a, t),
                    mix(b, other.b, t), mix(c, other.c, t), mix(d, other.d, t));
        }

        double interpolate(double first, double second, double third, double fourth) {
            return a * first + b * second + c * third + d * fourth;
        }
    }

    /** Null keeps the source quad, an empty list drops it, and other results replace it. */
    public List<CornerQuadSplitter.Piece> clip(Matrix4f matrix, BakedQuad quad, float[] brightness, int[] lights) {
        int[] data = quad.getVertexData();
        if (data.length != 32) return null;
        // Alpha-cut quads cannot occlude another surface. Avoid scanning their sprites here.
        if ((data[3] & data[11] & data[19] & data[27]) >>> 24 != 255) return null;
        Object parent = CornerQuadParentScope.parentFor(quad);
        Face face = project(matrix, data, parent instanceof BakedQuad original ? original : quad);
        if (face == null) return null;
        Face earlier = planes.get(face.key);
        boolean opaque = false;
        List<List<Vertex>> remaining = null;
        for (Face other = earlier; other != null; other = other.previous) {
            if (!face.overlaps(other)) continue;
            if (!StaticArchitectureGeometry.opaqueQuad(other.opacitySource)) continue;
            if (!opaque) {
                if (!StaticArchitectureGeometry.opaqueQuad(quad)) return null;
                opaque = true;
            }
            if (face.sameBoundary(other)) return List.of();
            List<List<Vertex>> input = remaining == null ? triangles(face) : remaining;
            List<List<Vertex>> next = new ArrayList<>();
            boolean removed = false;
            for (List<Vertex> polygon : input) {
                if (subtract(polygon, other, next)) removed = true;
                if (next.size() > MAX_PIECES) {
                    remember(face, earlier);
                    return null;
                }
            }
            if (removed) remaining = next;
            if (remaining != null && remaining.isEmpty()) return List.of();
        }
        remember(face, earlier);
        if (remaining == null || brightness == null || brightness.length != 4 || lights == null || lights.length != 4) {
            return null;
        }
        for (int i = 1; i < 4; i++) {
            if (data[i * 8 + 7] != data[7] || !Float.isFinite(brightness[i])) return null;
        }
        if (!Float.isFinite(brightness[0])) return null;
        List<CornerQuadSplitter.Piece> result = new ArrayList<>();
        for (List<Vertex> polygon : remaining) {
            if (polygon.size() == 3 || polygon.size() == 4) {
                Vertex last = polygon.get(polygon.size() - 1);
                result.add(piece(quad, data, brightness, lights, polygon.get(0), polygon.get(1), polygon.get(2), last));
                if (result.size() > MAX_PIECES) return null;
            } else {
                for (int i = 1; i + 1 < polygon.size(); i++) {
                    Vertex last = polygon.get(i + 1);
                    result.add(piece(quad, data, brightness, lights, polygon.get(0), polygon.get(i), last, last));
                    if (result.size() > MAX_PIECES) return null;
                }
            }
        }
        return result;
    }

    private void remember(Face face, Face earlier) {
        if (tracked >= MAX_TRACKED || earlier != null && earlier.bucketSize >= MAX_PER_BUCKET) return;
        if (earlier != null) {
            face.previous = earlier;
            face.bucketSize = earlier.bucketSize + 1;
        }
        planes.put(face.key, face);
        tracked++;
    }

    private Face project(Matrix4f matrix, int[] data, BakedQuad opacitySource) {
        for (int i = 0; i < 4; i++) {
            int offset = i * 8;
            double px = Float.intBitsToFloat(data[offset]);
            double py = Float.intBitsToFloat(data[offset + 1]);
            double pz = Float.intBitsToFloat(data[offset + 2]);
            coordinates[i] = matrix.m00() * px + matrix.m10() * py + matrix.m20() * pz + matrix.m30();
            coordinates[4 + i] = matrix.m01() * px + matrix.m11() * py + matrix.m21() * pz + matrix.m31();
            coordinates[8 + i] = matrix.m02() * px + matrix.m12() * py + matrix.m22() * pz + matrix.m32();
            if (!Double.isFinite(coordinates[i]) || !Double.isFinite(coordinates[4 + i]) || !Double.isFinite(coordinates[8 + i])) return null;
        }
        double ax = coordinates[1] - coordinates[0], ay = coordinates[5] - coordinates[4], az = coordinates[9] - coordinates[8];
        double bx = coordinates[2] - coordinates[0], by = coordinates[6] - coordinates[4], bz = coordinates[10] - coordinates[8];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double absX = Math.abs(nx), absY = Math.abs(ny), absZ = Math.abs(nz);
        int axis = absX >= absY && absX >= absZ ? 0 : absY >= absZ ? 1 : 2;
        int d = axis * 4, u = (axis + 1) % 3 * 4, v = (axis + 2) % 3 * 4;
        double normal = axis == 0 ? nx : axis == 1 ? ny : nz;
        if (Math.abs(normal) <= AREA_EPSILON) return null;
        int winding = normal > 0 ? 1 : -1;
        double slopeU = -(axis == 0 ? ny : axis == 1 ? nz : nx) / normal;
        double slopeV = -(axis == 0 ? nz : axis == 1 ? nx : ny) / normal;
        double offset = coordinates[d] - slopeU * coordinates[u] - slopeV * coordinates[v];
        int size = coordinates[u + 2] == coordinates[u + 3] && coordinates[v + 2] == coordinates[v + 3] && coordinates[d + 2] == coordinates[d + 3] ? 3 : 4;
        double[] points = new double[size * 3];
        double minU = coordinates[u], maxU = coordinates[u], minV = coordinates[v], maxV = coordinates[v];
        for (int i = 0; i < size; i++) {
            int next = (i + 1) % size;
            int after = (i + 2) % size;
            double cross = (coordinates[u + next] - coordinates[u + i]) * (coordinates[v + after] - coordinates[v + i]) - (coordinates[v + next] - coordinates[v + i]) * (coordinates[u + after] - coordinates[u + i]);
            if (cross * winding <= AREA_EPSILON
                    || Math.abs(coordinates[d + i] - (offset + slopeU * coordinates[u + i] + slopeV * coordinates[v + i])) > PLANE_EPSILON) return null;
            points[i * 3] = coordinates[u + i];
            points[i * 3 + 1] = coordinates[v + i];
            points[i * 3 + 2] = coordinates[d + i];
            minU = Math.min(minU, coordinates[u + i]);
            maxU = Math.max(maxU, coordinates[u + i]);
            minV = Math.min(minV, coordinates[v + i]);
            maxV = Math.max(maxV, coordinates[v + i]);
        }
        PlaneKey key = new PlaneKey(axis, winding, bucket(slopeU), bucket(slopeV), bucket(offset));
        return new Face(key, points, size, slopeU, slopeV, offset, minU, maxU, minV, maxV, opacitySource);
    }

    private static long bucket(double value) {
        return Math.round(value * 2048);
    }

    private static List<List<Vertex>> triangles(Face face) {
        Vertex a = new Vertex(face.u(0), face.v(0), 1, 0, 0, 0);
        Vertex b = new Vertex(face.u(1), face.v(1), 0, 1, 0, 0);
        Vertex c = new Vertex(face.u(2), face.v(2), 0, 0, 1, 0);
        if (face.size == 3) return List.of(List.of(a, b, c));
        Vertex d = new Vertex(face.u(3), face.v(3), 0, 0, 0, 1);
        return List.of(List.of(a, b, c), List.of(a, c, d));
    }

    private static boolean subtract(List<Vertex> polygon, Face occluder, List<List<Vertex>> result) {
        List<List<Vertex>> outside = new ArrayList<>(4);
        List<Vertex> inside = polygon;
        for (int i = 0; i < occluder.size; i++) {
            int end = (i + 1) % occluder.size;
            double u = occluder.u(i), v = occluder.v(i);
            double du = occluder.u(end) - u, dv = occluder.v(end) - v;
            int winding = occluder.key.winding;
            List<Vertex> next = new ArrayList<>();
            List<Vertex> strip = new ArrayList<>();
            Vertex previous = inside.get(inside.size() - 1);
            double prevDistance = side(previous, u, v, du, dv, winding);
            for (Vertex vertex : inside) {
                double distance = side(vertex, u, v, du, dv, winding);
                if (distance * prevDistance < 0) {
                    Vertex cut = previous.interpolate(vertex, prevDistance / (prevDistance - distance));
                    next.add(cut);
                    strip.add(cut);
                }
                if (distance >= 0) next.add(vertex);
                if (distance <= 0) strip.add(vertex);
                previous = vertex;
                prevDistance = distance;
            }
            if (!hasArea(next)) {
                result.add(polygon);
                return false;
            }
            if (hasArea(strip)) outside.add(strip);
            inside = next;
        }
        result.addAll(outside);
        return true;
    }

    private static boolean hasArea(List<Vertex> points) {
        if (points.size() < 3) return false;
        Vertex origin = points.get(0);
        for (int i = 1; i + 1 < points.size(); i++) {
            Vertex a = points.get(i), b = points.get(i + 1);
            if (Math.abs((a.u - origin.u) * (b.v - origin.v) - (a.v - origin.v) * (b.u - origin.u)) > AREA_EPSILON) {
                return true;
            }
        }
        return false;
    }

    private static double side(Vertex vertex, double u, double v, double du, double dv, int winding) {
        return winding * (du * (vertex.v - v) - dv * (vertex.u - u));
    }

    private static CornerQuadSplitter.Piece piece(BakedQuad quad, int[] source, float[] brightness, int[] lights,
                                                   Vertex a, Vertex b, Vertex c, Vertex d) {
        int[] data = new int[32], light = new int[4];
        float[] shade = new float[4];
        Vertex[] vertices = {a, b, c, d};
        for (int i = 0; i < 4; i++) {
            Vertex vertex = vertices[i];
            int offset = i * 8;
            data[offset] = interpolatedFloat(vertex, source, 0);
            data[offset + 1] = interpolatedFloat(vertex, source, 1);
            data[offset + 2] = interpolatedFloat(vertex, source, 2);
            int color = 0;
            for (int shift = 0; shift < 32; shift += 8) {
                color |= ((int) Math.round(vertex.interpolate((source[3] >>> shift) & 255, (source[11] >>> shift) & 255,
                        (source[19] >>> shift) & 255, (source[27] >>> shift) & 255))) << shift;
            }
            data[offset + 3] = color;
            data[offset + 4] = interpolatedFloat(vertex, source, 4);
            data[offset + 5] = interpolatedFloat(vertex, source, 5);
            data[offset + 6] = packedLight(vertex, source[6], source[14], source[22], source[30]);
            data[offset + 7] = source[7];
            light[i] = packedLight(vertex, lights[0], lights[1], lights[2], lights[3]);
            shade[i] = (float) vertex.interpolate(brightness[0], brightness[1], brightness[2], brightness[3]);
        }
        BakedQuad clipped = new BakedQuad(data, quad.getColorIndex(), quad.getFace(), quad.getSprite(), quad.hasShade());
        return new CornerQuadSplitter.Piece(clipped, shade, light);
    }

    private static int interpolatedFloat(Vertex vertex, int[] source, int field) {
        return Float.floatToRawIntBits((float) vertex.interpolate(Float.intBitsToFloat(source[field]),
                Float.intBitsToFloat(source[field + 8]), Float.intBitsToFloat(source[field + 16]),
                Float.intBitsToFloat(source[field + 24])));
    }

    private static int packedLight(Vertex vertex, int a, int b, int c, int d) {
        int low = (int) Math.round(vertex.interpolate(a & 65535, b & 65535, c & 65535, d & 65535));
        int high = (int) Math.round(vertex.interpolate(a >>> 16, b >>> 16, c >>> 16, d >>> 16));
        return (low & 65535) | (high << 16);
    }

    private static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
