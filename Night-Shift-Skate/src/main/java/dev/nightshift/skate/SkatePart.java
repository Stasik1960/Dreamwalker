package dev.nightshift.skate;

import java.util.Locale;

public sealed interface SkatePart permits SkatePart.Deck, SkatePart.Trucks, SkatePart.Wheels {
    enum Type {
        DECK("deck"), TRUCKS("trucks"), WHEELS("wheels");

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    Type type();

    String id();

    default String itemId() {
        return "part_" + type().id() + "_" + id();
    }

    default String translationKey() {
        return "item.night_shift_skate." + itemId();
    }

    enum Deck implements SkatePart {
        NIGHT_SHIFT(0.029, 1.00, 8),
        IRON_VESPERS(0.035, 1.04, 8),
        GRAVE_PROCESSION(0.080, 1.12, 10),
        CATHEDRAL_SPIRE(0.043, 1.19, 9),
        MOON_SICKLE(0.056, 1.06, 7),
        HUNTER_RELIQUARY(0.062, 1.14, 9),
        ROSE_WINDOW(0.061, 1.00, 7),
        GARGOYLE_WING(0.063, 1.09, 8);

        private final String id = name().toLowerCase(Locale.ROOT);
        private final double standingHeight;
        private final double pop;
        private final int flipTicks;

        Deck(double standingHeight, double pop, int flipTicks) {
            this.standingHeight = standingHeight;
            this.pop = pop;
            this.flipTicks = flipTicks;
        }

        @Override public Type type() { return Type.DECK; }
        @Override public String id() { return id; }
        public double standingHeight() { return standingHeight; }
        public double pop() { return pop; }
        public int flipTicks() { return flipTicks; }
    }

    enum Trucks implements SkatePart {
        NIGHT_SHIFT(0.092, 0.108, 1.00, 1.00),
        IRON_VESPERS(0.0654, 0.145, 1.13, 0.94),
        GRAVE_PROCESSION(0.086, 0.145, 0.89, 1.20),
        FLYING_BUTTRESS(0.074, 0.145, 0.93, 1.36),
        CHALICE_YOKE(0.072, 0.145, 1.24, 0.88),
        RIBCAGE(0.084, 0.145, 1.04, 1.15),
        IRON_GATE(0.063, 0.145, 0.97, 1.25),
        GARGOYLE_CLAW(0.080, 0.145, 1.18, 1.04);

        private final String id = name().toLowerCase(Locale.ROOT);
        private final double axleDrop;
        private final double halfTrack;
        private final double steering;
        private final double balance;

        Trucks(double axleDrop, double halfTrack, double steering, double balance) {
            this.axleDrop = axleDrop;
            this.halfTrack = halfTrack;
            this.steering = steering;
            this.balance = balance;
        }

        @Override public Type type() { return Type.TRUCKS; }
        @Override public String id() { return id; }
        public double axleDrop() { return axleDrop; }
        public double halfTrack() { return halfTrack; }
        public double steering() { return steering; }
        public double balance() { return balance; }
    }

    enum Wheels implements SkatePart {
        NIGHT_SHIFT(0.034, 1.00, 1.00, 1.00),
        IRON_VESPERS(0.039, 1.04, 1.07, 1.09),
        GRAVE_PROCESSION(0.046, 1.09, 0.94, 0.88),
        ROSARY_BEAD(0.035, 0.94, 1.21, 1.19),
        ROSE_COG(0.040, 1.10, 1.10, 1.24),
        STAINED_GLASS(0.041, 1.05, 0.98, 0.75),
        TOMB_ROLLER(0.051, 1.18, 0.91, 0.70),
        MOON_HUB(0.038, 1.12, 1.14, 1.07);

        private final String id = name().toLowerCase(Locale.ROOT);
        private final double radius;
        private final double topSpeed;
        private final double acceleration;
        private final double drag;

        Wheels(double radius, double topSpeed, double acceleration, double drag) {
            this.radius = radius;
            this.topSpeed = topSpeed;
            this.acceleration = acceleration;
            this.drag = drag;
        }

        @Override public Type type() { return Type.WHEELS; }
        @Override public String id() { return id; }
        public double radius() { return radius; }
        public double topSpeed() { return topSpeed; }
        public double acceleration() { return acceleration; }
        public double drag() { return drag; }
    }
}
