package dev.dreamwalker.bloodbornedw.architecture.window;

import java.util.List;

/** Reviewed descriptor keys; the common adapter owns registration and lifecycle. */
public final class WindowPrototypes {
    public static final String WOOD_KEY = "prototype_wood_window";
    public static final String THIN_KEY = "prototype_thin_window";
    public static final String WOOD_ID = "bloodborne_dw:" + WOOD_KEY;
    public static final String THIN_ID = "bloodborne_dw:" + THIN_KEY;
    public static final int GLOBAL_ORIENTATIONS = 8;
    public static final double SIDE_PROPOSAL_DELTA_DEGREES = 45;

    private WindowPrototypes() {}
    public static List<String> resourceKeys() { return List.of(WOOD_KEY, THIN_KEY); }
}
