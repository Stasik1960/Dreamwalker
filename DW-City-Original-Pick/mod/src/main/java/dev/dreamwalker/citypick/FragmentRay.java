package dev.dreamwalker.citypick;

/** Intersects a view ray with clipped source quads in the targeted cell. */
record FragmentRay(double x, double y, double z, double dx, double dy, double dz) {
    static final double EPSILON = 1.0 / 2048;

    double distance(short[] quads) {
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < quads.length; i += 12) {
            nearest = Math.min(nearest, triangle(quads, i, i + 3, i + 6));
            nearest = Math.min(nearest, triangle(quads, i, i + 6, i + 9));
        }
        return nearest;
    }

    private double triangle(short[] vertices, int a, int b, int c) {
        double ax = coordinate(vertices[a]), ay = coordinate(vertices[a + 1]), az = coordinate(vertices[a + 2]);
        double abx = coordinate(vertices[b]) - ax, aby = coordinate(vertices[b + 1]) - ay;
        double abz = coordinate(vertices[b + 2]) - az;
        double acx = coordinate(vertices[c]) - ax, acy = coordinate(vertices[c + 1]) - ay;
        double acz = coordinate(vertices[c + 2]) - az;
        double px = dy * acz - dz * acy, py = dz * acx - dx * acz, pz = dx * acy - dy * acx;
        double det = abx * px + aby * py + abz * pz;
        if (Math.abs(det) < 1e-10) return Double.POSITIVE_INFINITY;
        double tx = x - ax, ty = y - ay, tz = z - az;
        double u = (tx * px + ty * py + tz * pz) / det;
        if (u < -EPSILON || u > 1 + EPSILON) return Double.POSITIVE_INFINITY;
        double qx = ty * abz - tz * aby, qy = tz * abx - tx * abz, qz = tx * aby - ty * abx;
        double v = (dx * qx + dy * qy + dz * qz) / det;
        if (v < -EPSILON || u + v > 1 + EPSILON) return Double.POSITIVE_INFINITY;
        double t = (acx * qx + acy * qy + acz * qz) / det;
        return t < -EPSILON ? Double.POSITIVE_INFINITY : Math.max(0, t);
    }

    private static double coordinate(short value) {
        return Short.toUnsignedInt(value) / 65535.0;
    }
}
