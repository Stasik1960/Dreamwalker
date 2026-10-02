package ru.modelprops.model;

public record ModelTransform(
        float scale,
        float offsetX,
        float offsetY,
        float offsetZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        int revision
) {
    public static final ModelTransform IDENTITY = new ModelTransform(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0);

    public ModelTransform withRevision(int newRevision) {
        return new ModelTransform(scale, offsetX, offsetY, offsetZ, rotationX, rotationY, rotationZ, newRevision);
    }

    public boolean isValid() {
        return finite(scale) && finite(offsetX) && finite(offsetY) && finite(offsetZ)
                && finite(rotationX) && finite(rotationY) && finite(rotationZ)
                && scale >= 0.05F && scale <= 8.0F
                && between(offsetX, -2.0F, 2.0F)
                && between(offsetY, -2.0F, 2.0F)
                && between(offsetZ, -2.0F, 2.0F)
                && between(rotationX, -180.0F, 180.0F)
                && between(rotationY, -180.0F, 180.0F)
                && between(rotationZ, -180.0F, 180.0F);
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean between(float value, float min, float max) {
        return value >= min && value <= max;
    }
}
