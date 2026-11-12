package dev.dreamwalker.performance.mixin;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.CompositeGpuBatch", remap = false)
public abstract class CameraTransformMixin {
    /**
     * @author Dreamwalker contributors
     * @reason Keep the original camera tests without two temporary arrays per owner.
     */
    @Overwrite(remap = false)
    private static boolean cameraTransform(Matrix4f p, Matrix3f n, double x, double y, double z) {
        if (!dw$near(p.m03(), 0) || !dw$near(p.m13(), 0) || !dw$near(p.m23(), 0) || !dw$near(p.m33(), 1)) {
            return false;
        }
        float ax = p.m00(), ay = p.m01(), az = p.m02();
        float bx = p.m10(), by = p.m11(), bz = p.m12();
        float cx = p.m20(), cy = p.m21(), cz = p.m22();
        if (!dw$near(ax, n.m00()) || !dw$near(ay, n.m01()) || !dw$near(az, n.m02())
                || !dw$near(bx, n.m10()) || !dw$near(by, n.m11()) || !dw$near(bz, n.m12())
                || !dw$near(cx, n.m20()) || !dw$near(cy, n.m21()) || !dw$near(cz, n.m22())
                || !dw$near(dw$dot(ax, ay, az, ax, ay, az), 1)
                || !dw$near(dw$dot(bx, by, bz, bx, by, bz), 1)
                || !dw$near(dw$dot(cx, cy, cz, cx, cy, cz), 1)
                || !dw$near(dw$dot(ax, ay, az, bx, by, bz), 0)
                || !dw$near(dw$dot(ax, ay, az, cx, cy, cz), 0)
                || !dw$near(dw$dot(bx, by, bz, cx, cy, cz), 0)) {
            return false;
        }
        return Math.abs(p.m30() - (ax * x + bx * y + cx * z)) <= .01
                && Math.abs(p.m31() - (ay * x + by * y + cy * z)) <= .01
                && Math.abs(p.m32() - (az * x + bz * y + cz * z)) <= .01;
    }

    @Unique
    private static boolean dw$near(float value, float expected) {
        return Math.abs(value - expected) <= .002f;
    }

    @Unique
    private static float dw$dot(float ax, float ay, float az, float bx, float by, float bz) {
        return ax * bx + ay * by + az * bz;
    }
}
