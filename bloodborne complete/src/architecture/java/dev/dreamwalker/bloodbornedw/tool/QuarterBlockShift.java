package dev.dreamwalker.bloodbornedw.tool;

/** Adds fractional bits to the original signed half-block fields without moving existing builds. */
public final class QuarterBlockShift {
    private static final int AXIS_MASK = 63;
    private static final int FIRST_FRACTION = 24;
    private static final int LIMIT = 32;
    private static final int FLAGS = 7 << 21;
    private static final int VALID_BITS = (1 << 27) - 1;

    private QuarterBlockShift() {}

    public static int quarters(int value, int axis) {
        int half = value >>> (6 * axis) & AXIS_MASK;
        if (half >= 32) half -= 64;
        return 2 * half + (value >>> (FIRST_FRACTION + axis) & 1);
    }

    public static double offset(int value, int axis) {
        return quarters(value, axis) * 0.25;
    }

    public static boolean hasPose(int value) {
        return (value & (((1 << 21) - 1) | (7 << FIRST_FRACTION))) != 0;
    }

    public static int sanitize(int value) {
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(quarters(value, axis)) > LIMIT) return value & FLAGS;
        }
        return value & VALID_BITS;
    }

    public static int move(int value, int axis, int step) {
        int next = quarters(value, axis) + step;
        if (Math.abs(next) > LIMIT) return value;
        int shift = 6 * axis;
        int fraction = FIRST_FRACTION + axis;
        int mask = (AXIS_MASK << shift) | (1 << fraction);
        return (value & ~mask) | ((next >> 1 & AXIS_MASK) << shift) | ((next & 1) << fraction);
    }
}
