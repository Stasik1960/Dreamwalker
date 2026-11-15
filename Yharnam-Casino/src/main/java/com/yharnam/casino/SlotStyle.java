package com.yharnam.casino;

public enum SlotStyle {
    HUNTERS_WAGER("hunters_wager", "lantern", "cleaver", "vial", "moon", "rune", "bell", "eye", "coin"),
    ASTRAL_CHOIR("astral_choir", "crescent", "eye", "cluster", "observatory", "star", "orb", "rune", "bell"),
    CRIMSON_OATH("crimson_oath", "crown", "chalice", "rapier", "rose", "moon", "gem", "mask", "coin");

    private final String id;
    private final String[] symbols;

    SlotStyle(String id, String... symbols) {
        this.id = id;
        this.symbols = symbols;
    }

    public String id() {
        return id;
    }

    public String symbolKey(int index) {
        return "symbol.yharnam_casino." + symbols[index];
    }
}
