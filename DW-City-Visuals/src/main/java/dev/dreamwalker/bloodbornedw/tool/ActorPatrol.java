package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.ShapeContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/** Server-side route and manual command state for one actor. */
public final class ActorPatrol {
    private static final int MAX_POINTS = 256;
    private static final int STUCK_TICKS = 200;
    private static final int RETURN_WAIT_TICKS = 200;
    private static final int PATH_RETRY_TICKS = 20;
    private static final double MOVEMENT_THRESHOLD_SQUARED = 0.0625;
    private static final double ARRIVAL_RADIUS_SQUARED = 0.16;

    public interface Control {
        ActorPatrol dw$patrol();
        boolean dw$navigate(BlockPos ground);
        void dw$stopMoving();
    }

    private final List<BlockPos> points = new ArrayList<>();
    private boolean idleSounds;
    private boolean reverse;
    private int index;
    private int direction = 1;
    private BlockPos manual;
    private int wait;

    private Vec3d target;
    private Vec3d progressAnchor;
    private int stillTicks;
    private int retryTicks;
    private boolean started;
    private boolean parked;

    public static ActorPatrol of(RpMobEntity actor) {
        return ((Control) (Object) actor).dw$patrol();
    }

    public boolean idleSounds() { return idleSounds; }
    public boolean toggleIdleSounds() { return idleSounds = !idleSounds; }
    public boolean reverse() { return reverse; }

    public boolean toggleReverse() {
        reverse = !reverse;
        if (!reverse) direction = 1;
        return reverse;
    }

    public int addPoint(RpMobEntity actor, BlockPos ground) {
        Vec3d landing = destination(actor, ground);
        if (points.size() >= MAX_POINTS || landing == null || !clearLanding(actor, landing)) return 0;
        if (!points.isEmpty() && points.get(points.size() - 1).equals(ground)) return points.size();
        points.add(ground.toImmutable());
        index = points.size() - 1;
        direction = 1;
        manual = null;
        wait = 0;
        begin(actor, ground);
        return points.size();
    }

    public boolean manualOrder(RpMobEntity actor, BlockPos ground) {
        Vec3d landing = destination(actor, ground);
        if (landing == null || !clearLanding(actor, landing)) return false;
        manual = ground.toImmutable();
        wait = 0;
        begin(actor, ground);
        return true;
    }

    public void clear(RpMobEntity actor) {
        points.clear();
        index = 0;
        direction = 1;
        manual = null;
        wait = 0;
        started = false;
        parked = false;
        reverse = false;
        control(actor).dw$stopMoving();
    }

    public boolean active() { return manual != null || !points.isEmpty(); }

    public void tick(RpMobEntity actor) {
        if (!active() || actor.isCorpse() || actor.getFrozen() || !actor.isAlive()) return;

        if (wait > 0) {
            if (--wait > 0) return;
            manual = null;
            if (!points.isEmpty()) {
                index = closestPoint(actor);
                begin(actor, points.get(index));
            }
            return;
        }

        BlockPos ground = manual != null ? manual : points.get(index);
        if (!started) {
            begin(actor, ground);
            return;
        }

        if (target == null || !actor.getWorld().isChunkLoaded(ground)) {
            if (retryTicks-- <= 0) begin(actor, ground);
            return;
        }

        if (arrived(actor.getPos(), target)) {
            if (!parked) arrive(actor);
            return;
        }
        parked = false;
        if (actor.getPos().squaredDistanceTo(progressAnchor) >= MOVEMENT_THRESHOLD_SQUARED) {
            progressAnchor = actor.getPos();
            stillTicks = 0;
        } else if (++stillTicks >= STUCK_TICKS) {
            Vec3d landing = destination(actor, ground);
            if (landing != null && supported(actor, landing) && clearLanding(actor, landing)) {
                actor.stopRiding();
                actor.fallDistance = 0;
                actor.requestTeleport(landing.x, landing.y, landing.z);
                arrive(actor);
                return;
            }
            // A point may be obstructed after it was placed or occupied by another entity.
            // Keep trying to move to it without teleporting into the obstruction.
            stillTicks = STUCK_TICKS - PATH_RETRY_TICKS;
        }
        if (retryTicks-- <= 0 && actor.getNavigation().isIdle()) {
            control(actor).dw$navigate(ground);
            retryTicks = PATH_RETRY_TICKS;
        }
    }

    private void begin(RpMobEntity actor, BlockPos ground) {
        control(actor).dw$stopMoving();
        target = destination(actor, ground);
        started = true;
        parked = false;
        retryTicks = PATH_RETRY_TICKS;
        stillTicks = 0;
        progressAnchor = actor.getPos();
        if (target == null) return;
        actor.setFrozen(false);
        actor.setAiDisabled(false);
        if (!arrived(progressAnchor, target)) control(actor).dw$navigate(ground);
    }

    private void arrive(RpMobEntity actor) {
        control(actor).dw$stopMoving();
        if (manual != null) {
            if (points.isEmpty()) {
                manual = null;
                started = false;
            } else {
                wait = RETURN_WAIT_TICKS;
            }
        } else if (points.size() > 1) {
            if (reverse && (index + direction < 0 || index + direction >= points.size())) direction = -direction;
            index = Math.floorMod(index + direction, points.size());
            begin(actor, points.get(index));
        } else {
            parked = true;
        }
    }

    private int closestPoint(RpMobEntity actor) {
        double best = Double.POSITIVE_INFINITY;
        int nearest = 0;
        for (int i = 0; i < points.size(); i++) {
            BlockPos point = points.get(i);
            Vec3d resolved = destination(actor, point);
            Vec3d position = resolved != null ? resolved : Vec3d.ofBottomCenter(point.up());
            double distance = position.squaredDistanceTo(actor.getPos());
            if (distance < best) {
                nearest = i;
                best = distance;
            }
        }
        return nearest;
    }

    private static boolean arrived(Vec3d position, Vec3d destination) {
        double dx = position.x - destination.x;
        double dz = position.z - destination.z;
        return dx * dx + dz * dz < ARRIVAL_RADIUS_SQUARED && Math.abs(position.y - destination.y) < 0.6;
    }

    private static Vec3d destination(RpMobEntity actor, BlockPos ground) {
        if (!(actor.getWorld() instanceof ServerWorld world) || !world.isInBuildLimit(ground)
                || !world.getWorldBorder().contains(ground) || !world.isChunkLoaded(ground)) return null;
        VoxelShape shape = world.getBlockState(ground).getCollisionShape(world, ground, ShapeContext.absent());
        double y = ground.getY() + (shape.isEmpty() ? 0 : shape.getMax(Direction.Axis.Y));
        if (!world.isInBuildLimit(BlockPos.ofFloored(ground.getX() + 0.5, y, ground.getZ() + 0.5))) return null;
        return new Vec3d(ground.getX() + 0.5, y, ground.getZ() + 0.5);
    }

    private static boolean clearLanding(RpMobEntity actor, Vec3d landing) {
        // Ignore other mobs while validating a waypoint. Patrols may share a route.
        var feet = actor.getBoundingBox().offset(landing.subtract(actor.getPos())).contract(1.0E-5);
        return !actor.getWorld().getBlockCollisions(actor, feet).iterator().hasNext();
    }

    private static boolean supported(RpMobEntity actor, Vec3d landing) {
        var foundation = actor.getBoundingBox().offset(landing.subtract(actor.getPos()))
                .contract(1.0E-5).offset(0, -0.05, 0);
        return actor.getWorld().getBlockCollisions(actor, foundation).iterator().hasNext();
    }

    private static Control control(RpMobEntity actor) { return (Control) (Object) actor; }

    public void writeNbt(NbtCompound state) {
        if (idleSounds) state.putBoolean("IdleSounds", true);
        if (reverse) state.putBoolean("Reverse", true);
        if (!points.isEmpty()) {
            state.putLongArray("Points", points.stream().mapToLong(BlockPos::asLong).toArray());
            state.putInt("Index", index);
            if (direction < 0) state.putBoolean("GoingBack", true);
        }
        if (manual != null) {
            state.putLong("Manual", manual.asLong());
            if (wait > 0) state.putInt("ReturnWait", wait);
        }
    }

    public void readNbt(NbtCompound state) {
        idleSounds = state.getBoolean("IdleSounds");
        reverse = state.getBoolean("Reverse");
        direction = reverse && state.getBoolean("GoingBack") ? -1 : 1;
        points.clear();
        long[] saved = state.getLongArray("Points");
        for (int i = 0; i < Math.min(saved.length, MAX_POINTS); i++) points.add(BlockPos.fromLong(saved[i]));
        index = Math.max(0, Math.min(points.size() - 1, state.getInt("Index")));
        manual = state.contains("Manual", NbtElement.LONG_TYPE) ? BlockPos.fromLong(state.getLong("Manual")) : null;
        wait = manual == null || points.isEmpty() ? 0 : Math.max(0, Math.min(RETURN_WAIT_TICKS, state.getInt("ReturnWait")));
        started = false;
        parked = false;
    }
}
