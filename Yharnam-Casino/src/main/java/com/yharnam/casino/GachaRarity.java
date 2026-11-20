package com.yharnam.casino;

import java.util.Locale;

public enum GachaRarity {
    BLUE(74), PURPLE(20), ORANGE(5), RED(1);
    private final int weight;
    GachaRarity(int weight) { this.weight = weight; }
    public int weight() { return weight; }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** One uniform roll over 100 disjoint outcomes. */
    public static GachaRarity roll(int value) {
        if (value < 0 || value >= 100) throw new IllegalArgumentException("Roll must be in [0, 100)");
        if (value < 1) return RED;
        if (value < 6) return ORANGE;
        return value < 26 ? PURPLE : BLUE;
    }
}
