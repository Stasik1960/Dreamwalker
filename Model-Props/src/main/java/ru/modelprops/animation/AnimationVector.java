package ru.modelprops.animation;

/** A small immutable three-component vector used by animation data. */
public record AnimationVector(float x, float y, float z) {
    public static final AnimationVector ZERO = new AnimationVector(0.0F, 0.0F, 0.0F);
    public static final AnimationVector ONE = new AnimationVector(1.0F, 1.0F, 1.0F);

    public AnimationVector {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("Animation vectors must contain finite values");
        }
    }

    public AnimationVector add(AnimationVector other) {
        return new AnimationVector(x + other.x, y + other.y, z + other.z);
    }

    public AnimationVector multiply(AnimationVector other) {
        return new AnimationVector(x * other.x, y * other.y, z * other.z);
    }

    public static AnimationVector lerp(AnimationVector from, AnimationVector to, float amount) {
        return new AnimationVector(
                from.x + (to.x - from.x) * amount,
                from.y + (to.y - from.y) * amount,
                from.z + (to.z - from.z) * amount
        );
    }
}
