package dev.dreamwalker.bloodbornedw.composite;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.Box;
import org.joml.Matrix4d;

/** Approximates a horizontal 45-degree turn of each authored Studio volume with collision boxes. */
public final class DiagonalRpCollision {
    private static final double STEP = 1.0 / 16.0;
    private static final double EPSILON = 1e-8;
    // Very large custom scales must not make movement allocate an unbounded number of shapes.
    private static final int MAX_STRIPS = 128;

    private DiagonalRpCollision() {}

    public static boolean supports(Matrix4d transform) {
        double x = Math.abs(transform.m00()), z = Math.abs(transform.m02());
        double scale = Math.hypot(x, z);
        double tolerance = scale * EPSILON;
        return Double.isFinite(scale) && scale > EPSILON && transform.m11() > 0
                && Math.abs(transform.m01()) < tolerance && Math.abs(transform.m10()) < tolerance
                && Math.abs(x - z) < tolerance;
    }

    public static List<Box> boxes(List<Box> authored, Matrix4d transform) {
        if (authored.isEmpty()) return List.of();
        List<Box> result = new ArrayList<>(authored.size());
        for (Box box : authored) addStrips(box, transform, result);
        return List.copyOf(result);
    }

    private static void addStrips(Box box, Matrix4d transform, List<Box> result) {
        double[] localX = {box.minX, box.maxX, box.maxX, box.minX};
        double[] localZ = {box.minZ, box.minZ, box.maxZ, box.maxZ};
        double[] x = new double[4], z = new double[4];
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            x[i] = transform.m00() * localX[i] + transform.m20() * localZ[i];
            z[i] = transform.m02() * localX[i] + transform.m22() * localZ[i];
            minX = Math.min(minX, x[i]);
            maxX = Math.max(maxX, x[i]);
        }
        double width = maxX - minX;
        double minY = transform.m11() * box.minY + transform.m31();
        double maxY = transform.m11() * box.maxY + transform.m31();
        if (width <= EPSILON || maxY - minY <= EPSILON) return;
        int strips = Math.max(4, Math.min(MAX_STRIPS, (int) Math.ceil(width / STEP)));
        double left = minX;
        for (int strip = 0; strip < strips; strip++) {
            double right = strip + 1 == strips ? maxX : minX + width * (strip + 1) / strips;
            double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
            // The clipped polygon has vertices only at original corners or strip-edge crossings.
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) & 3;
                if (x[i] >= left && x[i] <= right) {
                    minZ = Math.min(minZ, z[i]);
                    maxZ = Math.max(maxZ, z[i]);
                }
                if (crosses(x[i], x[j], left)) {
                    double edge = atX(x[i], x[j], z[i], z[j], left);
                    minZ = Math.min(minZ, edge);
                    maxZ = Math.max(maxZ, edge);
                }
                if (crosses(x[i], x[j], right)) {
                    double edge = atX(x[i], x[j], z[i], z[j], right);
                    minZ = Math.min(minZ, edge);
                    maxZ = Math.max(maxZ, edge);
                }
            }
            if (maxZ - minZ > EPSILON) {
                result.add(new Box(left + transform.m30(), minY, minZ + transform.m32(),
                        right + transform.m30(), maxY, maxZ + transform.m32()));
            }
            left = right;
        }
    }

    private static boolean crosses(double a, double b, double x) {
        return (a < x && x < b) || (b < x && x < a);
    }

    private static double atX(double a, double b, double za, double zb, double x) {
        return za + (zb - za) * ((x - a) / (b - a));
    }
}
