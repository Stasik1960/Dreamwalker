package dev.dreamwalker.bloodbornerp.object;

import java.util.Map;

/** Registry aliases remain loadable; only ordinary construction and presentation are canonical. */
public final class RpObjectCompatibility {
    public static final Map<String,String> ALIASES = Map.of(
        "furniture_8", "furniture_1", "furniture_3", "furniture_10",
        "furniture_4", "furniture_11", "furniture_5", "furniture_12",
        "furniture_6", "furniture_13", "furniture_7", "furniture_14",
        "furniture_9", "furniture_2");
    private RpObjectCompatibility() {}
    public static String canonicalId(String id) { return ALIASES.getOrDefault(id,id); }
    public static boolean isCanonical(String id) { return !ALIASES.containsKey(id); }
}
