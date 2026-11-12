package dev.nightshift.skate;

public final class SkateControls {
    public static final int ACCELERATE = 1;
    public static final int BRAKE = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int JUMP = 1 << 4;
    public static final int CROUCH = 1 << 5;
    public static final int TRICK = 1 << 6;
    public static final int CONTINUOUS = ACCELERATE | BRAKE | LEFT | RIGHT | JUMP | CROUCH;

    private SkateControls() {}

    static boolean pressed(int input, int control) {
        return (input & control) != 0;
    }

    static int steer(int input) {
        return (pressed(input, RIGHT) ? 1 : 0) - (pressed(input, LEFT) ? 1 : 0);
    }
}
