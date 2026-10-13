package dev.dreamwalker.bloodbornedw.composite;

import java.util.ArrayList;
import java.util.List;

/** Simple physical defaults for explicitly opted-in, newly imported objects. */
public final class ImportedCollisionMath {
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Box {
            if (!Double.isFinite(x0) || !Double.isFinite(y0) || !Double.isFinite(z0)
                    || !Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(z1)
                    || x0 >= x1 || y0 >= y1 || z0 >= z1) throw new IllegalArgumentException("Invalid imported physical box");
        }
    }
    private ImportedCollisionMath() {}

    /** One ordinary AABB per input box, including 45-degree poses; no raster. */
    public static List<Box> transform(List<Box> boxes, int rotation, double mountY) {
        return transform(boxes, rotation, mountY, true);
    }
    /** Climbing zones are interaction volumes and are not physical face-cropped. */
    public static List<Box> transform(List<Box> boxes, int rotation, double mountY, boolean cropPhysicalFaces) {
        return transform(boxes, rotation, mountY, cropPhysicalFaces, 0, 0, 0);
    }
    /** Instance translation precedes the six comparisons with the root cell. */
    public static List<Box> transform(List<Box> boxes, int rotation, double mountY, boolean cropPhysicalFaces, double shiftX, double shiftY, double shiftZ) {
        if (boxes.isEmpty()) return List.of();
        double angle = Math.toRadians(Math.floorMod(rotation, 8) * 45);
        double c = Math.cos(angle), s = Math.sin(angle);
        if (rotation % 2 == 0) { c = Math.rint(c); s = Math.rint(s); }
        List<Box> transformed = new ArrayList<>();
        double[] low = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] high = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (Box box : boxes) {
            double x0 = Double.POSITIVE_INFINITY, z0 = x0, x1 = Double.NEGATIVE_INFINITY, z1 = x1;
            for (double x : new double[]{box.x0, box.x1}) for (double z : new double[]{box.z0, box.z1}) {
                double X = .5 + c * (x - .5) - s * (z - .5);
                double Z = .5 + s * (x - .5) + c * (z - .5);
                x0 = Math.min(x0, X); x1 = Math.max(x1, X); z0 = Math.min(z0, Z); z1 = Math.max(z1, Z);
            }
            Box value = new Box(x0 + shiftX, box.y0 + mountY + shiftY, z0 + shiftZ, x1 + shiftX, box.y1 + mountY + shiftY, z1 + shiftZ);
            transformed.add(value);
            low[0] = Math.min(low[0], value.x0); low[1] = Math.min(low[1], value.y0); low[2] = Math.min(low[2], value.z0);
            high[0] = Math.max(high[0], value.x1); high[1] = Math.max(high[1], value.y1); high[2] = Math.max(high[2], value.z1);
        }
        if (!cropPhysicalFaces) return List.copyOf(transformed);
        // Compare each of the six outer faces with the original root cell.
        // A small piece of a genuinely long object does not crop that object.
        for (int axis = 0; axis < 3; axis++) {
            if (low[axis] < 0 && low[axis] > -.5) low[axis] = 0;
            if (high[axis] > 1 && high[axis] < 1.5) high[axis] = 1;
        }
        List<Box> result = new ArrayList<>();
        for (Box value : transformed) {
            double x0 = Math.max(low[0], value.x0), y0 = Math.max(low[1], value.y0), z0 = Math.max(low[2], value.z0);
            double x1 = Math.min(high[0], value.x1), y1 = Math.min(high[1], value.y1), z1 = Math.min(high[2], value.z1);
            if (x0 < x1 && y0 < y1 && z0 < z1) result.add(new Box(x0, y0, z0, x1, y1, z1));
        }
        return List.copyOf(result);
    }
}
