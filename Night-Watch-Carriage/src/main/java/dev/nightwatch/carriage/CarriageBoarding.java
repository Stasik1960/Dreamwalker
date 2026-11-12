package dev.nightwatch.carriage;

import dev.nightwatch.carriage.CarriageEntity.Seat;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** The cushions, backrests and harness in model coordinates, with +Z facing forward. */
final class CarriageBoarding {
    private static final List<Box> LEFT = upholstery(.047, .753);
    private static final List<Box> RIGHT = upholstery(-.753, -.047);
    private static final List<Box> HARNESS = List.of(
        new Box(-.54, .985, 2.465, .54, 1.125, 2.64), // front crossbar and grips
        new Box(-.56, .92, 2.31, -.412, 1.16, 2.98),
        new Box(.412, .92, 2.31, .56, 1.16, 2.98),
        new Box(-.275, .985, 2.095, .275, 1.16, 2.29) // padded harness
    );
    // Vanilla sends the hit coordinates as floats. Leave a gap at the centre of the bench.
    private static final double HIT_TOLERANCE = .036;
    private static final double[] SIDE_Z = {-1.08, -.83, -.43, -.22, .52, .74, .94, 1.10, 1.22, 1.36};
    private static final double[] SIDE_TOP = {1.59, 1.748, 1.575, 1.51, 1.465, 1.40, 1.28, 1.17, 1.02, .69};
    private static final Box BACK = new Box(-.78, .50, -1.10, .78, 1.76, -.972);
    private static final Box FLOOR = new Box(-.82, .41, -1.04, .82, .65, 1.31);
    private static final Box CANOPY = new Box(-.82, 2.27, -.88, .82, 2.32, .55);

    private CarriageBoarding() { }

    private static List<Box> upholstery(double minX, double maxX) {
        return List.of(
            new Box(minX, .995, -.662, maxX, 1.205, .061),
            new Box(minX, 1.105, -.861, maxX, 1.737, -.631)
        );
    }

    private static List<Box> boxes(Seat seat) {
        return switch (seat) {
            case LEFT -> LEFT;
            case RIGHT -> RIGHT;
            case PULLER -> HARNESS;
        };
    }

    static CarriageEntity.BoardingHit raycast(Vec3d start, Vec3d end, Pose pose, boolean covered) {
        Vec3d from = pose.toLocal(start);
        Vec3d to = pose.toLocal(end);
        Seat nearestSeat = null;
        Vec3d nearestHit = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (Seat seat : Seat.values()) {
            for (Box region : boxes(seat)) {
                Vec3d hit = region.contains(from) ? from : region.raycast(from, to).orElse(null);
                if (hit == null) continue;
                double distance = from.squaredDistanceTo(hit);
                if (distance < nearestDistance) {
                    nearestSeat = seat;
                    nearestHit = hit;
                    nearestDistance = distance;
                }
            }
        }
        if (nearestSeat == null || nearestSeat != Seat.PULLER && blocked(from, nearestHit, covered)) return null;
        return new CarriageEntity.BoardingHit(nearestSeat, pose.toWorld(nearestHit));
    }

    static Seat at(Vec3d point, Pose pose) {
        Vec3d local = pose.toLocal(point);
        for (Seat seat : Seat.values()) {
            for (Box region : boxes(seat)) {
                if (region.expand(HIT_TOLERANCE).contains(local)) return seat;
            }
        }
        return null;
    }

    static Vec3d point(Seat seat, Pose pose) {
        return pose.toWorld(boxes(seat).get(0).getCenter());
    }

    static boolean occluded(Vec3d start, Vec3d end, Pose pose, boolean covered) {
        return blocked(pose.toLocal(start), pose.toLocal(end), covered);
    }

    private static boolean blocked(Vec3d start, Vec3d end, boolean covered) {
        return crossesSide(start, end, -.84) || crossesSide(start, end, .84) ||
            BACK.raycast(start, end).isPresent() || FLOOR.raycast(start, end).isPresent() ||
            (covered && CANOPY.raycast(start, end).isPresent());
    }

    private static boolean crossesSide(Vec3d start, Vec3d end, double sideX) {
        if (Math.abs(end.x - start.x) < 1e-6) return false;
        double fraction = (sideX - start.x) / (end.x - start.x);
        if (fraction <= 0 || fraction >= 1) return false;
        double z = MathHelper.lerp(fraction, start.z, end.z);
        double y = MathHelper.lerp(fraction, start.y, end.y);
        if (y < .49 || z < SIDE_Z[0] || z > SIDE_Z[SIDE_Z.length - 1]) return false;
        for (int i = 1; i < SIDE_Z.length; i++) {
            if (z <= SIDE_Z[i]) {
                double blend = (z - SIDE_Z[i - 1]) / (SIDE_Z[i] - SIDE_Z[i - 1]);
                return y < MathHelper.lerp(blend, SIDE_TOP[i - 1], SIDE_TOP[i]);
            }
        }
        return false;
    }

    record Pose(Vec3d position, float yaw, float pitch) {
        Vec3d toLocal(Vec3d worldPoint) {
            return worldPoint.subtract(position).rotateY(yaw * MathHelper.RADIANS_PER_DEGREE)
                .subtract(0, CarriageEntity.WHEEL_RADIUS, CarriageEntity.AXLE_Z)
                .rotateX(pitch * MathHelper.RADIANS_PER_DEGREE)
                .add(0, CarriageEntity.WHEEL_RADIUS, CarriageEntity.AXLE_Z);
        }

        Vec3d toWorld(Vec3d local) {
            return local.subtract(0, CarriageEntity.WHEEL_RADIUS, CarriageEntity.AXLE_Z)
                .rotateX(-pitch * MathHelper.RADIANS_PER_DEGREE)
                .add(0, CarriageEntity.WHEEL_RADIUS, CarriageEntity.AXLE_Z)
                .rotateY(-yaw * MathHelper.RADIANS_PER_DEGREE).add(position);
        }
    }
}
