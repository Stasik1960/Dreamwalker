package dev.dreamwalker.bloodbornedw.block;

import net.minecraft.util.StringIdentifiable;

/** Texture slot stored independently from the vanilla carrier state. */
public enum Visual implements StringIdentifiable {
    BASE("base"), ALT("alt");

    private final String id;

    Visual(String id) { this.id = id; }

    @Override public String asString() { return id; }
}
