package com.coraxberg.dwmagicconnect.client;

/** Shared rendering, hit testing and snapping geometry; zero always points up. */
public final class ClockMath {
    private ClockMath() {}
    public static double angle(int position, int divisions) { return position * Math.PI * 2 / divisions; }
    public static int snap(double dx, double dy, int divisions) {
        return Math.floorMod((int) Math.round(Math.atan2(dx, -dy) * divisions / (Math.PI * 2)), divisions);
    }
    public static double x(int position, int divisions, double radius) { return Math.sin(angle(position, divisions)) * radius; }
    public static double y(int position, int divisions, double radius) { return -Math.cos(angle(position, divisions)) * radius; }
    public static int pick(double dx, double dy, int hour, int minute, double radius) {
        if (Math.hypot(dx, dy) < 6 || Math.hypot(dx, dy) > radius + 6) return -1;
        double hx = x(hour, 12, radius * .48), hy = y(hour, 12, radius * .48);
        double mx = x(minute, 60, radius * .78), my = y(minute, 60, radius * .78);
        double hd = Math.hypot(dx - hx, dy - hy), md = Math.hypot(dx - mx, dy - my);
        // Separate tip handles let both hands be grabbed even when they overlap.
        if (Math.min(hd, md) <= 9) return hd <= md ? 0 : 1;
        double hs = segmentDistance(dx, dy, hx, hy), ms = segmentDistance(dx, dy, mx, my);
        return Math.min(hs, ms) <= 5 ? (hs <= ms ? 0 : 1) : -1;
    }
    private static double segmentDistance(double x, double y, double ex, double ey) {
        double t = Math.max(0, Math.min(1, (x * ex + y * ey) / (ex * ex + ey * ey)));
        return Math.hypot(x - t * ex, y - t * ey);
    }
}
