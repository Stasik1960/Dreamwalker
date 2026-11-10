package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.composite.overlaymixin.CompositeLightingViewAccess;
import dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess;
import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import dev.dreamwalker.bloodbornedw.visual.CityFacadesClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

/** Subtracts earlier opaque faces from later faces on the same side of a plane during a mesh build. */
public final class CoplanarQuads {
    private static final double PLANE_EPSILON = 0.00001;
    private static final double AREA_EPSILON = 0.0000000001;
    private static final int MAX_TRACKED = 4096;
    private static final int MAX_PER_BUCKET = 64;
    private static final int MAX_PIECES = 32;

    private final Map<PlaneKey, List<Face>> planes = new HashMap<>();
    private final double[] coordinates = new double[12];
    private Map<Neighbor, List<Face>> compactFaces;
    private int tracked;

    private record Neighbor(BlockPos pos, Direction side) {}

    private record PlaneKey(int axis, int winding, long slopeU, long slopeV, long offset) {}
    private record Point(double u, double v, double depth) {}
    private record Face(PlaneKey key, Point[] points, double slopeU, double slopeV, double offset,
                        double minU, double maxU, double minV, double maxV, BakedQuad opacitySource) {
        boolean overlaps(Face other) {
            if (minU >= other.maxU || maxU <= other.minU || minV >= other.maxV || maxV <= other.minV) {
                return false;
            }
            for (Point point : points) {
                double distance = point.depth - (other.offset + other.slopeU * point.u + other.slopeV * point.v);
                if (Math.abs(distance) > PLANE_EPSILON) return false;
            }
            for (Point point : other.points) {
                double distance = point.depth - (offset + slopeU * point.u + slopeV * point.v);
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
        return clip(matrix, quad, brightness, lights, null);
    }

    public List<CornerQuadSplitter.Piece> clip(Matrix4f matrix, BakedQuad quad, float[] brightness, int[] lights,
                                               StaticLightingViewAccess view) {
        int[] data = quad.getVertexData();
        if (data.length != 32) return null;
        // Alpha-cut quads cannot occlude another surface. Avoid scanning their sprites here.
        if ((data[3] & data[11] & data[19] & data[27]) >>> 24 != 255) return null;
        Object parent = CornerQuadParentScope.parentFor(quad);
        Face face = project(matrix, data, parent instanceof BakedQuad original ? original : quad);
        if (face == null) return null;
        List<Face> earlier = planes.get(face.key);
        List<Face> neighbors = compactNeighbors(face, matrix, view);
        boolean opaque = false;
        List<List<Vertex>> remaining = null;
        int neighborCount = neighbors.size();
        int count = neighborCount + (earlier == null ? 0 : earlier.size());
        for (int i = 0; i < count; i++) {
            Face other = i < neighborCount ? neighbors.get(i) : earlier.get(i - neighborCount);
            if (!face.overlaps(other) || !StaticArchitectureGeometry.opaqueQuad(other.opacitySource)) continue;
            if (!opaque) {
                if (!StaticArchitectureGeometry.opaqueQuad(quad)) return null;
                opaque = true;
            }
            List<List<Vertex>> input = remaining == null ? triangles(face.points) : remaining;
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

    /** A visible compact wall face wins over overhanging decoration, regardless of mesh order. */
    private List<Face> compactNeighbors(Face face, Matrix4f matrix, StaticLightingViewAccess view) {
        if (view == null || Math.abs(face.slopeU) > PLANE_EPSILON || Math.abs(face.slopeV) > PLANE_EPSILON) {
            return List.of();
        }
        Matrix4f pose = view.dw$transform();
        if (pose == null || !sameBasis(matrix, pose)) return List.of();
        float x = matrix.m30() - pose.m30();
        float y = matrix.m31() - pose.m31();
        float z = matrix.m32() - pose.m32();
        int axis = face.key.axis;
        double depth = axis == 0 ? x : axis == 1 ? y : z;
        double u = axis == 0 ? y : axis == 1 ? z : x;
        double v = axis == 0 ? z : axis == 1 ? x : y;
        int normalCell = (int) Math.floor(face.offset - depth - face.key.winding * PLANE_EPSILON);
        if (normalCell < -1 || normalCell > 1) return List.of();
        int minU = Math.max(-1, (int) Math.floor(face.minU - u + PLANE_EPSILON));
        int maxU = Math.min(1, (int) Math.ceil(face.maxU - u - PLANE_EPSILON) - 1);
        int minV = Math.max(-1, (int) Math.floor(face.minV - v + PLANE_EPSILON));
        int maxV = Math.min(1, (int) Math.ceil(face.maxV - v - PLANE_EPSILON) - 1);
        if (minU > maxU || minV > maxV || normalCell == 0 && minU == 0 && maxU == 0 && minV == 0 && maxV == 0) {
            return List.of();
        }
        List<Face> result = null;
        BlockPos root = view.dw$root();
        Direction.Axis directionAxis = axis == 0 ? Direction.Axis.X : axis == 1 ? Direction.Axis.Y : Direction.Axis.Z;
        Direction side = Direction.from(directionAxis, face.key.winding > 0
                ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        for (int a = minU; a <= maxU; a++) {
            for (int b = minV; b <= maxV; b++) {
                if (normalCell == 0 && a == 0 && b == 0) continue;
                int dx = axis == 0 ? normalCell : axis == 1 ? b : a;
                int dy = axis == 0 ? a : axis == 1 ? normalCell : b;
                int dz = axis == 0 ? b : axis == 1 ? a : normalCell;
                Neighbor key = new Neighbor(root.add(dx, dy, dz), side);
                if (compactFaces == null) compactFaces = new HashMap<>();
                List<Face> compact = compactFaces.get(key);
                if (compact == null) {
                    compact = readCompactFaces(view, key, x + dx, y + dy, z + dz);
                    compactFaces.put(key, compact);
                }
                for (Face candidate : compact) {
                    if (candidate.key.axis != axis || candidate.key.winding != face.key.winding
                            || !face.overlaps(candidate)) continue;
                    if (result == null) result = new ArrayList<>(4);
                    result.add(candidate);
                }
            }
        }
        return result == null ? List.of() : result;
    }

    private List<Face> readCompactFaces(StaticLightingViewAccess view, Neighbor key, float x, float y, float z) {
        var world = view.dw$delegate();
        BlockPos pos = key.pos;
        if (view instanceof CompositeLightingViewAccess snapshot) snapshot.dw$dependency(pos);
        var state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ImportedCompactBlock block) || !state.get(ImportedCompactBlock.COMPACT)
                || block.spec.translucent || state.getRenderType() != BlockRenderType.MODEL) return List.of();
        var client = MinecraftClient.getInstance();
        if (client.world == null || DebugAdjustments.hasPose(DebugAdjustments.renderPose(client.world, pos, state))) return List.of();
        var model = CityFacadesClient.compactOccluder(client.getBlockRenderManager().getModel(state), pos, state, world);
        if (model == null) return List.of();
        BlockPos outside = pos.offset(key.side);
        if (view instanceof CompositeLightingViewAccess snapshot) snapshot.dw$dependency(outside);
        var offset = state.getModelOffset(world, pos);
        Matrix4f transform = new Matrix4f().translation(x + (float) offset.x, y + (float) offset.y, z + (float) offset.z);
        long seed = state.getRenderingSeed(pos);
        List<Face> faces = new ArrayList<>();
        addCompactFaces(faces, model.getQuads(state, null, Random.create(seed)), transform);
        if (cullingDependencyVisible(view, outside) && Block.shouldDrawSide(state, world, pos, key.side, outside)) {
            addCompactFaces(faces, model.getQuads(state, key.side, Random.create(seed)), transform);
        }
        return faces;
    }

    private static boolean cullingDependencyVisible(StaticLightingViewAccess view, BlockPos outside) {
        if (view instanceof CompositeLightingViewAccess) return true;
        BlockPos root = view.dw$root();
        // Native terrain automatically rebuilds its own section and sections
        // within one block of the changed culling neighbor.
        return ChunkSectionPos.from(root).asLong() == ChunkSectionPos.from(outside).asLong()
                || Math.abs(outside.getX() - root.getX()) <= 1 && Math.abs(outside.getY() - root.getY()) <= 1
                        && Math.abs(outside.getZ() - root.getZ()) <= 1;
    }

    private void addCompactFaces(List<Face> faces, List<BakedQuad> quads, Matrix4f matrix) {
        if (quads.size() > MAX_PER_BUCKET) return;
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertexData();
            if (data.length != 32 || (data[3] & data[11] & data[19] & data[27]) >>> 24 != 255) continue;
            Face face = project(matrix, data, quad);
            if (face != null) faces.add(face);
        }
    }

    private static boolean sameBasis(Matrix4f a, Matrix4f b) {
        return a.m00() == b.m00() && a.m01() == b.m01() && a.m02() == b.m02()
                && a.m10() == b.m10() && a.m11() == b.m11() && a.m12() == b.m12()
                && a.m20() == b.m20() && a.m21() == b.m21() && a.m22() == b.m22();
    }

    private void remember(Face face, List<Face> earlier) {
        if (tracked >= MAX_TRACKED || earlier != null && earlier.size() >= MAX_PER_BUCKET) return;
        if (earlier == null) earlier = planes.computeIfAbsent(face.key, ignored -> new ArrayList<>(4));
        earlier.add(face);
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
        Point[] points = new Point[size];
        double minU = coordinates[u], maxU = coordinates[u], minV = coordinates[v], maxV = coordinates[v];
        for (int i = 0; i < size; i++) {
            int next = (i + 1) % size;
            int after = (i + 2) % size;
            double cross = (coordinates[u + next] - coordinates[u + i]) * (coordinates[v + after] - coordinates[v + i]) - (coordinates[v + next] - coordinates[v + i]) * (coordinates[u + after] - coordinates[u + i]);
            if (cross * winding <= AREA_EPSILON
                    || Math.abs(coordinates[d + i] - (offset + slopeU * coordinates[u + i] + slopeV * coordinates[v + i])) > PLANE_EPSILON) return null;
            points[i] = new Point(coordinates[u + i], coordinates[v + i], coordinates[d + i]);
            minU = Math.min(minU, coordinates[u + i]);
            maxU = Math.max(maxU, coordinates[u + i]);
            minV = Math.min(minV, coordinates[v + i]);
            maxV = Math.max(maxV, coordinates[v + i]);
        }
        PlaneKey key = new PlaneKey(axis, winding, bucket(slopeU), bucket(slopeV), bucket(offset));
        return new Face(key, points, slopeU, slopeV, offset, minU, maxU, minV, maxV, opacitySource);
    }

    private static long bucket(double value) {
        return Math.round(value * 2048);
    }

    private static List<List<Vertex>> triangles(Point[] points) {
        Vertex a = new Vertex(points[0].u, points[0].v, 1, 0, 0, 0);
        Vertex b = new Vertex(points[1].u, points[1].v, 0, 1, 0, 0);
        Vertex c = new Vertex(points[2].u, points[2].v, 0, 0, 1, 0);
        if (points.length == 3) return List.of(List.of(a, b, c));
        Vertex d = new Vertex(points[3].u, points[3].v, 0, 0, 0, 1);
        return List.of(List.of(a, b, c), List.of(a, c, d));
    }

    private static boolean subtract(List<Vertex> polygon, Face occluder, List<List<Vertex>> result) {
        List<List<Vertex>> outside = new ArrayList<>(4);
        List<Vertex> inside = polygon;
        Point[] points = occluder.points;
        for (int i = 0; i < points.length; i++) {
            Point a = points[i], b = points[(i + 1) % points.length];
            List<Vertex> next = new ArrayList<>();
            List<Vertex> strip = new ArrayList<>();
            Vertex previous = inside.get(inside.size() - 1);
            double prevDistance = side(previous, a, b, occluder.key.winding);
            for (Vertex vertex : inside) {
                double distance = side(vertex, a, b, occluder.key.winding);
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

    private static double side(Vertex vertex, Point a, Point b, int winding) {
        return winding * ((b.u - a.u) * (vertex.v - a.v) - (b.v - a.v) * (vertex.u - a.u));
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
