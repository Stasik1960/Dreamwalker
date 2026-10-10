package ru.modelprops.animation;

import java.util.LinkedHashMap;
import java.util.Map;

/** A sampled pose. The reserved target {@code root} transforms the complete item. */
public final class AnimationPose {
    public static final AnimationPose EMPTY = new AnimationPose(Map.of());

    private final Map<String, AnimationTransform> transforms;

    public AnimationPose(Map<String, AnimationTransform> transforms) {
        this.transforms = Map.copyOf(transforms);
    }

    public AnimationTransform transform(String target) {
        return transforms.getOrDefault(target, AnimationTransform.IDENTITY);
    }

    public Map<String, AnimationTransform> transforms() {
        return transforms;
    }

    public boolean isEmpty() {
        return transforms.isEmpty();
    }

    public AnimationPose combine(AnimationPose overlay) {
        if (isEmpty()) {
            return overlay;
        }
        if (overlay.isEmpty()) {
            return this;
        }
        Map<String, AnimationTransform> combined = new LinkedHashMap<>(transforms);
        overlay.transforms.forEach((target, transform) -> combined.merge(
                target, transform, AnimationTransform::combine
        ));
        return new AnimationPose(combined);
    }
}
