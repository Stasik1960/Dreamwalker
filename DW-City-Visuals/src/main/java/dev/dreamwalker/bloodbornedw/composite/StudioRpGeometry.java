package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.InteractionReach;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/** Runtime geometry for the entity-backed Studio models, using GeckoLib's transform. */
public final class StudioRpGeometry {
    private static final String SEAT_PREFIX = "studio-rp/";
    private static final Map<RpObjectEntity, Layout> CACHE = new WeakHashMap<>();
    private record Key(StudioLayouts.Pose pose, Vec3d position, float yaw, float pitch, float scale) {}
    public record Layout(Key key, List<Box> players, List<Box> blocks, List<Box> outline,
            List<Box> seatTargets, List<BenchSeating.Point> seats, Box extent) {}
    private StudioRpGeometry() {}

    public static Layout layout(RpObjectEntity object) {
        var pose = StudioLayouts.pose(object);
        if (pose == null) return null;
        float scale = object.asset().scale() * object.objectScale();
        Key key = new Key(pose, object.getPos(), object.getYaw(), object.getPitch(), scale);
        synchronized (CACHE) {
            Layout cached = CACHE.get(object);
            if (cached != null && cached.key.equals(key)) return cached;
            Layout fresh = build(key, StudioLayouts.model(object).anchor());
            CACHE.put(object, fresh);
            return fresh;
        }
    }

    private static Layout build(Key key, Vec3d anchor) {
        var position = key.position();
        Matrix4d transform = new Matrix4d().translate(position.x, position.y, position.z)
                .mul(orientation(key.yaw(), key.pitch(), key.scale())).translate(-anchor.x, -anchor.y, -anchor.z);
        var pose = key.pose();
        List<Box> players = boxes(pose.players(), transform);
        List<Box> blocks = boxes(pose.blocks(), transform);
        List<Box> outline = boxes(pose.outline(), transform);
        List<Box> targets = new ArrayList<>();
        List<BenchSeating.Point> seats = new ArrayList<>();
        for (var seat : pose.seats()) {
            var surface = seat.contact();
            Vec3d point = position(transform, surface);
            Vec3d front = direction(transform, seat.forward());
            seats.add(new BenchSeating.Point(point, (float) Math.toDegrees(Math.atan2(-front.x, front.z))));
            targets.add(StudioFootprints.transformBounds(new Box(surface.x - .3, surface.y - .1, surface.z - .3,
                    surface.x + .3, surface.y + .04, surface.z + .3), transform));
        }
        Box extent = null;
        for (var group : List.of(players, blocks, outline, targets))
            for (var box : group) extent = extent == null ? box : extent.union(box);
        return new Layout(key, players, blocks, outline, List.copyOf(targets), List.copyOf(seats), extent);
    }

    public static Vec3d renderOffset(RpObjectEntity object, float yaw, float pitch) {
        var model = StudioLayouts.model(object);
        if (model == null) return Vec3d.ZERO;
        var pose = model.pose(0, object.isOpen());
        var delta = (pose == null ? Vec3d.ZERO : pose.offset()).subtract(model.anchor());
        if (delta.equals(Vec3d.ZERO)) return delta;
        return direction(orientation(yaw, pitch, object.asset().scale() * object.objectScale()), delta);
    }

    private static Matrix4d orientation(float yaw, float pitch, float scale) {
        return new Matrix4d().rotateY(Math.toRadians(yaw - 90)).rotateZ(Math.toRadians(pitch)).scale(scale);
    }

    private static List<Box> boxes(List<Box> local, Matrix4d transform) {
        return local.stream().map(box -> StudioFootprints.transformBounds(box, transform)).toList();
    }

    private static Vec3d position(Matrix4d matrix, Vec3d input) {
        var out = matrix.transformPosition(new Vector3d(input.x, input.y, input.z));
        return new Vec3d(out.x, out.y, out.z);
    }

    private static Vec3d direction(Matrix4d matrix, Vec3d input) {
        var out = matrix.transformDirection(new Vector3d(input.x, input.y, input.z));
        return new Vec3d(out.x, out.y, out.z);
    }

    public static ActionResult seat(RpObjectEntity object, PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND || player.isSpectator() || player.isSneaking() || !player.getMainHandStack().isEmpty())
            return ActionResult.PASS;
        var layout = layout(object);
        if (layout == null || layout.seats().isEmpty()) return ActionResult.PASS;
        var start = player.getEyePos();
        var end = start.add(player.getRotationVector().multiply(InteractionReach.attack(player)));
        Vec3d nearest = null;
        for (Box box : layout.seatTargets()) {
            var hit = box.raycast(start, end).orElse(null);
            if (hit != null && (nearest == null || hit.squaredDistanceTo(start) < nearest.squaredDistanceTo(start))) nearest = hit;
        }
        if (nearest == null) return ActionResult.PASS;
        if (!(object.getWorld() instanceof ServerWorld world)) return ActionResult.SUCCESS;
        var target = new BenchSeating.Target(object.getBlockPos(), identity(object), layout.seats());
        return BenchSeating.sit(world, target, player, nearest);
    }

    public static boolean rpSeat(String identity) { return identity.startsWith(SEAT_PREFIX); }

    public static boolean validSeat(ServerWorld world, BlockPos root, String identity) {
        try {
            String uuid = identity.substring(SEAT_PREFIX.length(), identity.indexOf('/', SEAT_PREFIX.length()));
            var entity = world.getEntity(UUID.fromString(uuid));
            return entity instanceof RpObjectEntity object && !object.isRemoved()
                    && object.getBlockPos().equals(root) && identity(object).equals(identity);
        } catch (IllegalArgumentException | IndexOutOfBoundsException malformed) {
            return false;
        }
    }

    private static String identity(RpObjectEntity object) {
        return SEAT_PREFIX + object.getUuidAsString() + "/" + object.isOpen() + "/" + object.getPos()
                + "/" + object.getYaw() + "/" + object.getPitch() + "/" + object.objectScale();
    }
}
