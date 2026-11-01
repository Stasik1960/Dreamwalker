package dev.dreamwalker.bloodbornedw.visual;

import net.minecraft.util.math.Box;

/** Conservative render-only envelopes derived from the alternate tree geometry. */
public final class AltTreeBounds {
    private AltTreeBounds() {}

    public static Box local(String id) {
        return switch (id) {
            case "tree1", "bloodborne_rp:tree1" -> TREE1;
            case "tree2", "bloodborne_rp:tree2" -> TREE2;
            case "tree3", "bloodborne_rp:tree3" -> TREE3;
            default -> null;
        };
    }

    private static final Box TREE1 = new Box(-6.37, -.74, -4.89, 5.20, 16.87, 5.43);
    private static final Box TREE2 = new Box(-5.75, -.74, -6.07, 5.16, 11.88, 4.39);
    private static final Box TREE3 = new Box(-5.91, -.74, -5.27, 4.82, 12.39, 5.58);

    public record Snapshot(double x, double y, double z, float yaw, float pitch, float scale, Box local, Box original, Box result) {}

    public static Box world(Box local, double x, double y, double z, float yaw, float pitch, float scale, Box original) {
        double a = Math.toRadians(yaw - 90), c = Math.cos(a), s = Math.sin(a);
        double p = Math.toRadians(pitch), pc = Math.cos(p), ps = Math.sin(p);
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int i = 0; i < 8; i++) {
            double lx = ((i & 1) == 0 ? local.minX : local.maxX) * scale;
            double ly = ((i & 2) == 0 ? local.minY : local.maxY) * scale;
            double lz = ((i & 4) == 0 ? local.minZ : local.maxZ) * scale;
            double px = lx * pc - ly * ps, py = lx * ps + ly * pc;
            double wx = x + px * c + lz * s, wy = y + py + .01, wz = z - px * s + lz * c;
            minX = Math.min(minX, wx); minY = Math.min(minY, wy); minZ = Math.min(minZ, wz);
            maxX = Math.max(maxX, wx); maxY = Math.max(maxY, wy); maxZ = Math.max(maxZ, wz);
        }
        return original.union(new Box(minX, minY, minZ, maxX, maxY, maxZ).expand(.03 * Math.max(1, Math.abs(scale))));
    }
}
