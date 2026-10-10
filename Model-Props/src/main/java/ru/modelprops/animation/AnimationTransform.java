package ru.modelprops.animation;

import java.util.Objects;

/** Position is measured in model pixels, rotation in degrees, and scale is multiplicative. */
public record AnimationTransform(
        AnimationVector position,
        AnimationVector rotation,
        AnimationVector scale
) {
    public static final AnimationTransform IDENTITY = new AnimationTransform(
            AnimationVector.ZERO, AnimationVector.ZERO, AnimationVector.ONE
    );

    public AnimationTransform {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(scale, "scale");
    }

    /** Combines two independently sampled poses: offsets/rotations add and scales multiply. */
    public AnimationTransform combine(AnimationTransform overlay) {
        return new AnimationTransform(
                position.add(overlay.position),
                rotation.add(overlay.rotation),
                scale.multiply(overlay.scale)
        );
    }

    public static AnimationTransform interpolate(AnimationTransform from, AnimationTransform to, float amount) {
        return new AnimationTransform(
                AnimationVector.lerp(from.position, to.position, amount),
                AnimationVector.lerp(from.rotation, to.rotation, amount),
                AnimationVector.lerp(from.scale, to.scale, amount)
        );
    }
}
