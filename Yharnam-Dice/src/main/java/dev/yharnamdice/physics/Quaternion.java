package dev.yharnamdice.physics;

/** Unit quaternion in network-friendly x,y,z,w order. */
public record Quaternion(double x, double y, double z, double w) {
    public static final Quaternion IDENTITY = new Quaternion(0, 0, 0, 1);

    public Quaternion normalized() {
        double n = Math.sqrt(x*x + y*y + z*z + w*w);
        return n < 1.0e-12 ? IDENTITY : new Quaternion(x/n, y/n, z/n, w/n);
    }
    public Quaternion multiply(Quaternion b) {
        return new Quaternion(w*b.x + x*b.w + y*b.z - z*b.y,
                w*b.y - x*b.z + y*b.w + z*b.x,
                w*b.z + x*b.y - y*b.x + z*b.w,
                w*b.w - x*b.x - y*b.y - z*b.z);
    }
    public Quaternion conjugate() { return new Quaternion(-x, -y, -z, w); }
    public Vec3 rotate(Vec3 v) {
        Quaternion p = new Quaternion(v.x(), v.y(), v.z(), 0);
        Quaternion r = multiply(p).multiply(conjugate());
        return new Vec3(r.x(), r.y(), r.z());
    }
    public static Quaternion axisAngle(Vec3 axis, double radians) {
        Vec3 n = axis.normalized();
        double s = Math.sin(radians * .5);
        return new Quaternion(n.x()*s, n.y()*s, n.z()*s, Math.cos(radians*.5));
    }
    public static Quaternion integrate(Quaternion q, Vec3 angularVelocity, double dt) {
        double angle = angularVelocity.length() * dt;
        return angle < 1e-9 ? q : axisAngle(angularVelocity, angle).multiply(q).normalized();
    }
}
