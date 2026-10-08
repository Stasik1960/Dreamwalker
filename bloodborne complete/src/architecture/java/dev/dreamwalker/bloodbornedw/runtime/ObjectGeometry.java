package dev.dreamwalker.bloodbornedw.runtime;

import java.util.List;

/** Geometry is authored by the catalog; the transaction layer never infers it from a carrier. */
public final class ObjectGeometry {
    /** Contact and insignificant floating-point penetration are not placement conflicts. */
    public static final double PHYSICAL_EPSILON = 1e-5;
    private ObjectGeometry() {}

    public record Cell(int x, int y, int z) implements Comparable<Cell> {
        public static final Cell ORIGIN = new Cell(0, 0, 0);
        public Cell add(Cell offset) {
            return new Cell(Math.addExact(x, offset.x), Math.addExact(y, offset.y), Math.addExact(z, offset.z));
        }
        public Cell subtract(Cell origin) {
            return new Cell(Math.subtractExact(x, origin.x), Math.subtractExact(y, origin.y), Math.subtractExact(z, origin.z));
        }
        public Cell rotate(int quarterTurns) {
            return switch (Math.floorMod(quarterTurns, 4)) {
                case 0 -> this;
                case 1 -> new Cell(Math.negateExact(z), y, x);
                case 2 -> new Cell(Math.negateExact(x), y, Math.negateExact(z));
                default -> new Cell(z, y, Math.negateExact(x));
            };
        }
        @Override public int compareTo(Cell other) {
            int value = Integer.compare(x, other.x);
            if (value == 0) value = Integer.compare(y, other.y);
            return value == 0 ? Integer.compare(z, other.z) : value;
        }
    }

    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Non-finite point");
        }
    }

    /** Cell-local box. Render overhang must be explicitly clipped into its owning cells. */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box {
            for (double n : new double[]{minX, minY, minZ, maxX, maxY, maxZ})
                if (!Double.isFinite(n) || n < 0 || n > 1) throw new IllegalArgumentException("Invalid local box");
            if (minX >= maxX || minY >= maxY || minZ >= maxZ) throw new IllegalArgumentException("Empty box");
        }
        public boolean overlaps(Box other) {
            return Math.min(maxX,other.maxX)-Math.max(minX,other.minX)>PHYSICAL_EPSILON
                    && Math.min(maxY,other.maxY)-Math.max(minY,other.minY)>PHYSICAL_EPSILON
                    && Math.min(maxZ,other.maxZ)-Math.max(minZ,other.minZ)>PHYSICAL_EPSILON;
        }
        public Box rotate(int quarterTurns) {
            return switch (Math.floorMod(quarterTurns, 4)) {
                case 0 -> this;
                case 1 -> new Box(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX);
                case 2 -> new Box(1 - maxX, minY, 1 - maxZ, 1 - minX, maxY, 1 - minZ);
                default -> new Box(minZ, minY, 1 - maxX, maxZ, maxY, 1 - minX);
            };
        }
        /** Parametric ray distance; the caller's direction need not be unit length. */
        public double rayDistance(Cell cell, Point origin, Point direction, double limit) {
            if (!Double.isFinite(limit) || limit < 0) throw new IllegalArgumentException("Invalid ray limit");
            double near = 0, far = limit;
            double[] lo = {cell.x + minX, cell.y + minY, cell.z + minZ};
            double[] hi = {cell.x + maxX, cell.y + maxY, cell.z + maxZ};
            double[] start = {origin.x, origin.y, origin.z}, ray = {direction.x, direction.y, direction.z};
            for (int axis = 0; axis < 3; axis++) {
                if (ray[axis] == 0) {
                    if (start[axis] < lo[axis] || start[axis] > hi[axis]) return Double.POSITIVE_INFINITY;
                } else {
                    double first = (lo[axis] - start[axis]) / ray[axis], second = (hi[axis] - start[axis]) / ray[axis];
                    near = Math.max(near, Math.min(first, second));
                    far = Math.min(far, Math.max(first, second));
                    if (near > far) return Double.POSITIVE_INFINITY;
                }
            }
            return near;
        }
    }

    public record Footprint(List<Box> collision, List<Box> selection) {
        public static final Footprint EMPTY = new Footprint(List.of(), List.of());
        public Footprint {
            collision = List.copyOf(collision);
            selection = List.copyOf(selection);
        }
        public Footprint rotate(int quarterTurns) {
            return new Footprint(collision.stream().map(box -> box.rotate(quarterTurns)).toList(),
                    selection.stream().map(box -> box.rotate(quarterTurns)).toList());
        }
        public boolean collides(Footprint other) {
            return collision.stream().anyMatch(a -> other.collision.stream().anyMatch(a::overlaps));
        }
    }
}
