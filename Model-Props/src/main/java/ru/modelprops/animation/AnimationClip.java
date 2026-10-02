package ru.modelprops.animation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record AnimationClip(
        String name,
        int durationMillis,
        boolean loop,
        Map<String, List<Keyframe>> tracks
) {
    public AnimationClip {
        LinkedHashMap<String, List<Keyframe>> immutableTracks = new LinkedHashMap<>();
        tracks.forEach((target, frames) -> immutableTracks.put(target, List.copyOf(frames)));
        tracks = Map.copyOf(immutableTracks);
    }

    public record Keyframe(int timeMillis, AnimationTransform transform, Interpolation interpolation) {
    }

    public enum Interpolation {
        STEP,
        LINEAR,
        SMOOTH;

        public static Interpolation parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Unknown interpolation '" + value
                        + "' (expected step, linear, or smooth)");
            }
        }

        public float apply(float amount) {
            float clamped = Math.max(0.0F, Math.min(1.0F, amount));
            return switch (this) {
                case STEP -> 0.0F;
                case LINEAR -> clamped;
                case SMOOTH -> clamped * clamped * (3.0F - 2.0F * clamped);
            };
        }
    }
}
