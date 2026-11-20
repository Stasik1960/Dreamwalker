package com.yharnam.casino;

import java.util.Random;

/** Ball and rotor on the cross-section of the modelled single-zero wheel, in blocks and seconds. */
public final class RouletteSpin {
    public static final double CX = -1.07, CZ = -.024, ROTOR_Y = .8816;
    private static final double TAU = 2 * Math.PI, PITCH = TAU / 37, DT = .01, RADIUS = .029;
    private static final int MAX_TICKS = 300, CAPTURE_LIMIT = 250;
    private static final int[] ORDER = RouletteRound.wheelOrder();
    public record Frame(double rotor, double angle, double radius, double height) {
        public double x() { return CX + radius * Math.sin(angle); }
        public double z() { return CZ - radius * Math.cos(angle); }
    }
    private final Frame[] frames = new Frame[MAX_TICKS + 1];
    private int ticks, pocket;

    public RouletteSpin(long seed, double initialRotor) {
        Random random = new Random(seed);
        double rotor = initialRotor % TAU, omega = 1.9 + random.nextDouble() * 1.8;
        double angle = random.nextDouble() * TAU, radius = .594;
        double tangential = -(2.7 + random.nextDouble() * .7), radial = 0;
        double y = floor(radius) + RADIUS, vy = .13;
        int locked = 0;
        boolean captured = false;
        double offset = 0;
        frames[0] = new Frame(rotor, angle, radius, y);
        for (int tick = 1; tick <= MAX_TICKS; tick++) {
            for (int step = 0; step < 5; step++) {
                rotor += omega * DT;
                omega = Math.max(0, omega - .34 * DT);
                if (captured) {
                    offset *= .91;
                    radius += (.319 - radius) * .09;
                    angle = rotor + locked * PITCH + offset;
                    y = floor(radius) + RADIUS;
                    continue;
                }
                double oldAngle = angle, oldRelative = angle - rotor + omega * DT;
                double gradient = slope(radius);
                boolean grounded = y <= floor(radius) + RADIUS + .002;
                // Gravity along the sloped race opposes the outward centrifugal acceleration.
                radial += ((grounded ? -14.0 * gradient / (1 + gradient * gradient) : 0)
                        + tangential * tangential / radius) * DT;
                radial *= Math.exp(-(grounded ? 4.8 : 1.2) * DT);
                double drag = radius > .48 ? .46 : .95;
                tangential *= Math.exp(-drag * DT);
                angle += tangential / radius * DT;
                radius += radial * DT;
                if (radius > .605) { radius = .605; radial = -Math.abs(radial) * .20; }
                if (radius < .303) { radius = .303; radial = Math.abs(radial) * .26; }
                vy -= 6.8 * DT;
                y += vy * DT;
                double bed = floor(radius) + RADIUS;
                if (y <= bed) {
                    double normalSpeed = vy - gradient * radial;
                    y = bed;
                    vy = gradient * radial - Math.min(0, normalSpeed) * .46;
                }
                // Eight fixed diamonds, then the rotating brass fret at each pocket boundary.
                boolean diamond = radius > .529 && radius < .576
                    && crossed(oldAngle - TAU/16, angle - TAU/16, TAU/8);
                boolean fret = radius < .361 && crossed(oldRelative + PITCH/2, angle - rotor + PITCH/2, PITCH);
                if ((diamond || fret) && y < bed + .024) {
                    vy = Math.max(vy, (diamond ? .55 : .26) + random.nextDouble() * .25);
                    radial -= .09 + random.nextDouble() * .14;
                    if (fret) tangential += (omega * radius - tangential) * .55;
                }
                if (radius < .346 && Math.abs(tangential / radius - omega) < 1.7
                        && y < bed + .018) {
                    locked = (int)Math.round((angle - rotor) / PITCH);
                    captured = true;
                    offset = angle - rotor - locked * PITCH;
                    pocket = Math.floorMod(locked, 37);
                }
            }
            // Keep time bounded and leave enough frames for the rotor and ball to settle.
            if (!captured && tick == CAPTURE_LIMIT) {
                locked = (int)Math.round((angle - rotor) / PITCH);
                captured = true;
                offset = angle - rotor - locked * PITCH;
                pocket = Math.floorMod(locked, 37);
            }
            frames[tick] = new Frame(rotor, angle, radius, y);
            ticks = tick;
            if (captured && omega == 0 && Math.abs(offset) < .0002 && Math.abs(radius-.319)<.0002)
                break;
        }
    }

    private static boolean crossed(double from, double to, double pitch) {
        return Math.floor(from / pitch) != Math.floor(to / pitch);
    }
    private static double slope(double r) {
        if (r > .545) return 1.12;
        if (r > .50) return .72;
        if (r > .358) return .15;
        return r > .319 ? .12 : -.15;
    }
    private static double floor(double r) {
        if (r > .545) return .945 + (r - .545)*1.12;
        if (r > .50) return .9126 + (r - .50)*.72;
        if (r > .358) return .905 + (r - .358)*.0535;
        return .900 + Math.abs(r-.319)*.128;
    }
    public Frame at(double tick) {
        double time = Math.max(0, Math.min(ticks, tick));
        int index = (int)time;
        Frame a = frames[index];
        if (index == ticks || time == index) return a;
        Frame b = frames[index+1];
        double t = time - index;
        return new Frame(a.rotor+(b.rotor-a.rotor)*t, a.angle+(b.angle-a.angle)*t,
            a.radius+(b.radius-a.radius)*t, a.height+(b.height-a.height)*t);
    }
    public int duration() { return ticks; }
    public int number() { return ORDER[pocket]; }
}
