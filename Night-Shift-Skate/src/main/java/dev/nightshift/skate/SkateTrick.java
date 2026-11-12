package dev.nightshift.skate;

import static dev.nightshift.skate.SkateControls.*;

public enum SkateTrick {
    // The order is shared with the client animation IDs. Keep the existing IDs when adding tricks.
    NONE(0, ""),
    POP_SHOVE_IT(100, "Pop Shove-it"),
    KICKFLIP(200, "Kickflip"),
    HEELFLIP(200, "Heelflip"),
    SHOVE_IT_360(300, "360 Shove-it"),
    VARIAL_KICKFLIP(350, "Varial Kickflip"),
    INDY(220, "Indy"),
    MELON(220, "Melon"),
    NOSEGRAB(260, "Nosegrab"),
    TAILGRAB(260, "Tailgrab"),
    FLIP_360(400, "Tre Flip"),
    MANUAL(0, "Manual"),
    NOSE_MANUAL(0, "Nose Manual"),
    BOARDSLIDE(0, "Boardslide"),
    WALLRIDE(250, "Wallride");

    private static final SkateTrick[] BY_ID = values();
    private final int points;
    private final String englishName;

    SkateTrick(int points, String englishName) {
        this.points = points;
        this.englishName = englishName;
    }

    public static SkateTrick fromId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : NONE;
    }

    public String translationKey() {
        return "night_shift_skate.trick." + ordinal();
    }

    public String englishName() {
        return englishName;
    }

    int points() {
        return points;
    }

    public boolean isGrab() {
        return switch (this) {
            case INDY, MELON, NOSEGRAB, TAILGRAB -> true;
            default -> false;
        };
    }

    boolean reversesDeck() {
        return this == POP_SHOVE_IT || this == VARIAL_KICKFLIP;
    }

    static SkateTrick select(int input) {
        if (pressed(input, GRAB)) {
            if (pressed(input, BRAKE)) return TAILGRAB;
            if (pressed(input, ACCELERATE)) return NOSEGRAB;
            return steer(input) < 0 ? MELON : INDY;
        }
        if (!pressed(input, TRICK)) return NONE;
        if (pressed(input, CROUCH)) return VARIAL_KICKFLIP;
        if (pressed(input, BRAKE)) return SHOVE_IT_360;
        return switch (steer(input)) {
            case -1 -> KICKFLIP;
            case 1 -> HEELFLIP;
            default -> pressed(input, ACCELERATE) ? FLIP_360 : POP_SHOVE_IT;
        };
    }
}
