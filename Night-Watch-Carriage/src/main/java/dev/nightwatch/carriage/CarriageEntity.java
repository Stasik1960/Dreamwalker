package dev.nightwatch.carriage;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.util.hit.HitResult;

import java.util.EnumMap;
import java.util.UUID;

public final class CarriageEntity extends Entity {
    public static final double BOARDING_RANGE = 4.25;
    public static final double WHEEL_RADIUS = .860;
    static final double AXLE_Z = -.338;
    private static final float PARKED_PITCH = 4f;
    private static final float HITCHED_PITCH = -1.5f;
    private static final EntityDimensions FOLDED = EntityDimensions.changing(2.24f, 1.80f);
    private static final EntityDimensions RAISED = EntityDimensions.changing(2.24f, 2.77f);
    private static final TrackedData<Integer> LEFT = id();
    private static final TrackedData<Integer> RIGHT = id();
    private static final TrackedData<Integer> PULLER = id();
    private static final TrackedData<Boolean> HOOD = flag();
    private static final TrackedData<Boolean> LAMPS = flag();
    private static final TrackedData<Boolean> PULLER_CONTROLS = flag();
    private static final TrackedData<Float> SPEED =
        DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public enum Seat {
        LEFT("left", .385, .44, -.398),
        RIGHT("right", -.385, .44, -.398),
        PULLER("puller", 0, 0, 2.025);

        public final String key;
        private final Vec3d offset;

        Seat(String key, double x, double y, double z) {
            this.key = key;
            this.offset = new Vec3d(x, y, z);
        }
    }

    public record BoardingHit(Seat seat, Vec3d point) { }

    private final EnumMap<Seat, UUID> restoredSeats = new EnumMap<>(Seat.class);
    private final EnumMap<CarriagePackets.Action, Integer> actionTicks = new EnumMap<>(CarriagePackets.Action.class);
    private Seat requestedSeat;
    private int movementKeys;
    private int inputTick;
    private double drive;
    private int lerpSteps;
    private double targetX, targetY, targetZ;
    private float targetYaw;
    private float wheel, previousWheel;
    private float hood = 1f, previousHood = 1f;
    private float pitch = PARKED_PITCH, previousPitch = PARKED_PITCH;

    public CarriageEntity(EntityType<? extends CarriageEntity> type, World world) {
        super(type, world);
        setStepHeight(.6f);
    }

    private static TrackedData<Integer> id() {
        return DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.INTEGER);
    }

    private static TrackedData<Boolean> flag() {
        return DataTracker.registerData(CarriageEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(LEFT, -1);
        dataTracker.startTracking(RIGHT, -1);
        dataTracker.startTracking(PULLER, -1);
        dataTracker.startTracking(HOOD, true);
        dataTracker.startTracking(LAMPS, false);
        dataTracker.startTracking(PULLER_CONTROLS, false);
        dataTracker.startTracking(SPEED, 0f);
    }

    private static TrackedData<Integer> tracker(Seat seat) {
        return switch (seat) {
            case LEFT -> LEFT;
            case RIGHT -> RIGHT;
            case PULLER -> PULLER;
        };
    }

    public int occupant(Seat seat) {
        return dataTracker.get(tracker(seat));
    }

    public Seat seatOf(Entity rider) {
        if (rider == null || rider.getVehicle() != this) return null;
        for (Seat seat : Seat.values()) {
            if (occupant(seat) == rider.getId()) return seat;
        }
        return null;
    }

    public boolean hasPuller() { return occupant(Seat.PULLER) >= 0; }
    public boolean hoodRaised() { return dataTracker.get(HOOD); }
    public boolean lanternsOn() { return dataTracker.get(LAMPS); }
    public float speed() { return dataTracker.get(SPEED); }

    public Seat controlSeat() {
        if (occupant(Seat.LEFT) >= 0 && !dataTracker.get(PULLER_CONTROLS)) return Seat.LEFT;
        return Seat.PULLER;
    }

    public boolean controls(PlayerEntity player) {
        return hasPuller() && seatOf(player) == controlSeat();
    }

    private Seat firstFreeSeat() {
        for (Seat seat : Seat.values()) {
            if (occupant(seat) < 0) return seat;
        }
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof PlayerEntity && getPassengerList().size() < 3;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (getWorld().isClient) return;
        Seat seat = requestedSeat;
        if (seat == null) {
            for (Seat saved : Seat.values()) {
                if (passenger.getUuid().equals(restoredSeats.get(saved)) && occupant(saved) < 0) {
                    seat = saved;
                    restoredSeats.remove(saved);
                    break;
                }
            }
        }
        if (seat == null || occupant(seat) >= 0) seat = firstFreeSeat();
        if (seat != null) {
            dataTracker.set(tracker(seat), passenger.getId());
            resetForSeatChange(null, seat);
            updatePassengerPosition(passenger);
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (getWorld().isClient) return;
        for (Seat seat : Seat.values()) {
            if (occupant(seat) == passenger.getId()) {
                dataTracker.set(tracker(seat), -1);
                resetForSeatChange(seat, null);
                break;
            }
        }
    }

    private void resetForSeatChange(Seat before, Seat after) {
        if (before == Seat.LEFT || after == Seat.LEFT || before == Seat.PULLER || after == Seat.PULLER) {
            dataTracker.set(PULLER_CONTROLS, false);
            movementKeys = 0;
            if (!hasPuller()) drive = 0;
        }
    }

    public boolean canBoardFrom(PlayerEntity player) {
        if (player.isSpectator() || !player.isAlive() || isRemoved() || player.getWorld() != getWorld()) return false;
        if (player.getVehicle() == this) return true;
        if (player.hasVehicle() || squaredDistanceTo(player) > BOARDING_RANGE * BOARDING_RANGE) return false;
        Vec3d target = getPos().add(0, 1.1, 0);
        return getWorld().raycast(new RaycastContext(player.getEyePos(), target,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player)).getType() == HitResult.Type.MISS;
    }

    boolean moveToSeat(PlayerEntity player, Seat destination) {
        if (getWorld().isClient || !canBoardFrom(player) || occupant(destination) >= 0) return false;
        if (destination == Seat.PULLER && !clearPullerAt(getYaw(), 0, 0, 0)) return false;
        if (player.getVehicle() == this) {
            Seat current = seatOf(player);
            if (current == null) return false;
            dataTracker.set(tracker(current), -1);
            dataTracker.set(tracker(destination), player.getId());
            resetForSeatChange(current, destination);
            updatePassengerPosition(player);
            return true;
        }
        requestedSeat = destination;
        try {
            return player.startRiding(this);
        } finally {
            requestedSeat = null;
        }
    }

    public BoardingHit raycastBoarding(Vec3d start, Vec3d end, float tickDelta) {
        boolean covered = getWorld().isClient ? visualHood(tickDelta) > .8f : hoodRaised();
        return CarriageBoarding.raycast(start, end, boardingPose(tickDelta), covered);
    }

    public Vec3d boardingPoint(Seat seat, float tickDelta) {
        return CarriageBoarding.point(seat, boardingPose(tickDelta));
    }

    public Vec3d boardingPacketPoint(Vec3d hit, float tickDelta) {
        return currentBoardingPose().toWorld(boardingPose(tickDelta).toLocal(hit));
    }

    private CarriageBoarding.Pose currentBoardingPose() {
        return new CarriageBoarding.Pose(getPos(), getYaw(), hasPuller() ? HITCHED_PITCH : PARKED_PITCH);
    }

    private CarriageBoarding.Pose boardingPose(float tickDelta) {
        if (!getWorld().isClient) return currentBoardingPose();
        return new CarriageBoarding.Pose(getLerpedPos(tickDelta),
            MathHelper.lerpAngleDegrees(tickDelta, prevYaw, getYaw()), visualPitch(tickDelta));
    }

    @Override
    public ActionResult interactAt(PlayerEntity player, Vec3d offset, Hand hand) {
        if (hand != Hand.MAIN_HAND || player.isSpectator()) return ActionResult.PASS;
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (player.getVehicle() == this) return interact(player, hand);
        Vec3d point = getPos().add(offset);
        CarriageBoarding.Pose pose = currentBoardingPose();
        Seat seat = CarriageBoarding.at(point, pose);
        if (seat == null) return interact(player, hand);
        Vec3d eye = player.getEyePos();
        double reach = player.isCreative() ? BOARDING_RANGE : 3.0;
        if (eye.squaredDistanceTo(point) > reach * reach ||
            (seat != Seat.PULLER && CarriageBoarding.occluded(eye, point, pose, hoodRaised())) ||
            getWorld().raycast(new RaycastContext(eye, point, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, player)).getType() != HitResult.Type.MISS) {
            return ActionResult.CONSUME;
        }
        boolean boarded = moveToSeat(player, seat);
        player.sendMessage(Text.translatable(boarded ? "hint.nightwatchcarriage.panel" :
            "hint.nightwatchcarriage.unavailable"), true);
        return ActionResult.CONSUME;
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND || player.isSpectator()) return ActionResult.PASS;
        if (!getWorld().isClient) {
            player.sendMessage(Text.translatable(player.getVehicle() == this ? "hint.nightwatchcarriage.panel"
                : "hint.nightwatchcarriage.aim_seat"), true);
        }
        return ActionResult.success(getWorld().isClient);
    }

    void acceptInput(PlayerEntity sender, int keys) {
        if (!controls(sender)) return;
        movementKeys = keys & 15;
        inputTick = age;
    }

    void acceptAction(PlayerEntity sender, CarriagePackets.Action action) {
        Seat seat = seatOf(sender);
        if (seat == null) return;
        if (action.seat != null) {
            moveToSeat(sender, action.seat);
            return;
        }
        if (age - actionTicks.getOrDefault(action, -10) < 4) return;
        switch (action) {
            case TOGGLE_DRIVER -> {
                if (seat != Seat.LEFT || !hasPuller()) return;
                dataTracker.set(PULLER_CONTROLS, !dataTracker.get(PULLER_CONTROLS));
                movementKeys = 0;
            }
            case TOGGLE_HOOD -> {
                if (!hoodRaised() && getWorld().getBlockCollisions(this, RAISED.getBoxAt(getPos())).iterator().hasNext()) {
                    sender.sendMessage(Text.translatable("hint.nightwatchcarriage.roof_blocked"), true);
                    return;
                }
                dataTracker.set(HOOD, !hoodRaised());
            }
            case TOGGLE_LAMPS -> dataTracker.set(LAMPS, !lanternsOn());
            default -> { return; }
        }
        actionTicks.put(action, age);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            tickVisuals();
        } else {
            tickMotion();
        }
    }

    private void tickMotion() {
        if (age - inputTick > 7) movementKeys = 0;
        if (!hasPuller()) movementKeys = 0;
        int forward = ((movementKeys & 1) != 0 ? 1 : 0) - ((movementKeys & 2) != 0 ? 1 : 0);
        int steer = ((movementKeys & 8) != 0 ? 1 : 0) - ((movementKeys & 4) != 0 ? 1 : 0);
        double desired = forward > 0 ? .255 : forward < 0 ? -.105 : 0.;
        double acceleration = desired == 0 ? .040 : .023;
        drive = MathHelper.stepTowards((float) drive, (float) desired, (float) acceleration);
        if (isTouchingWater()) drive *= .65;
        float yaw = getYaw();
        if (steer != 0 && isOnGround()) {
            float turn = steer * (forward < 0 ? -1f : 1f) * (1.9f + 10f * (float) Math.abs(drive));
            if (clearPullerAt(yaw + turn, 0, 0, 0)) yaw += turn;
        }
        setYaw(MathHelper.wrapDegrees(yaw));
        double radians = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double x = -Math.sin(radians) * drive;
        double z = Math.cos(radians) * drive;
        if (hasPuller() && !clearPullerAt(yaw, x, 0, z)) {
            x = 0;
            z = 0;
            drive = 0;
        }
        double y = hasNoGravity() ? 0. : Math.max(-.70, (getVelocity().y - .06) * .98);
        Vec3d before = getPos();
        setVelocity(x, y, z);
        move(MovementType.SELF, getVelocity());
        if (horizontalCollision) drive *= .38;
        double distance = getPos().subtract(before).horizontalLength();
        dataTracker.set(SPEED, distance < .0005 ? 0f : (float) (distance * 20));
    }

    private boolean clearPullerAt(float yaw, double x, double y, double z) {
        return !Double.isNaN(pullerStep(yaw, x, y, z));
    }

    private double pullerStep(float yaw, double x, double y, double z) {
        Vec3d p = Seat.PULLER.offset.rotateY(-yaw * MathHelper.RADIANS_PER_DEGREE).add(getPos()).add(x, y, z);
        Box standing = new Box(p.x-.36,p.y+.04,p.z-.36,p.x+.36,p.y+1.84,p.z+.36);
        if (!getWorld().getBlockCollisions(this, standing).iterator().hasNext()) return 0;
        return !getWorld().getBlockCollisions(this, standing.offset(0,.5,0)).iterator().hasNext() ? .5 : Double.NaN;
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw,
                                               float packetPitch, int steps, boolean interpolate) {
        targetX = x;
        targetY = y;
        targetZ = z;
        targetYaw = yaw;
        lerpSteps = Math.max(1, Math.min(steps, 3));
    }

    private void tickVisuals() {
        Vec3d before = getPos();
        if (lerpSteps > 0) {
            setPosition(getX() + (targetX - getX()) / lerpSteps, getY() + (targetY - getY()) / lerpSteps,
                getZ() + (targetZ - getZ()) / lerpSteps);
            setYaw(getYaw() + MathHelper.wrapDegrees(targetYaw - getYaw()) / lerpSteps);
            lerpSteps--;
        }
        Vec3d delta = getPos().subtract(before);
        double radians = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double signedDistance = delta.x * -Math.sin(radians) + delta.z * Math.cos(radians);
        previousWheel = wheel;
        if (Math.abs(signedDistance) < 1.5) {
            wheel += (float) Math.toDegrees(signedDistance / WHEEL_RADIUS);
        }
        if (Math.abs(wheel) > 1440) {
            float shift = Math.copySign(1440f, wheel);
            previousWheel -= shift;
            wheel -= shift;
        }
        previousHood = hood;
        hood = age <= 2 ? (hoodRaised() ? 1 : 0) : MathHelper.stepTowards(hood, hoodRaised() ? 1f : 0f, .048f);
        previousPitch = pitch;
        pitch = MathHelper.stepTowards(pitch, hasPuller() ? HITCHED_PITCH : PARKED_PITCH, .58f);
    }

    public float visualHood(float tickDelta) {
        float t = MathHelper.lerp(tickDelta, previousHood, hood);
        return t * t * (3f - 2f * t);
    }

    public float visualPitch(float tickDelta) { return MathHelper.lerp(tickDelta, previousPitch, pitch); }
    public float visualWheel(float tickDelta) { return MathHelper.lerp(tickDelta, previousWheel, wheel); }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        Seat seat = seatOf(passenger);
        if (seat == null) return;
        Vec3d offset = seat.offset;
        if (seat == Seat.PULLER) {
            double step = pullerStep(getYaw(), 0, 0, 0);
            if (step > 0) offset = offset.add(0, step, 0);
        } else {
            float angle = getWorld().isClient ? pitch : hasPuller() ? HITCHED_PITCH : PARKED_PITCH;
            offset = offset.subtract(0, WHEEL_RADIUS, AXLE_Z)
                .rotateX(-angle * MathHelper.RADIANS_PER_DEGREE).add(0, WHEEL_RADIUS, AXLE_Z);
        }
        Vec3d position = offset.rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE).add(getPos());
        updater.accept(passenger, position.x, position.y, position.z);
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity rider) {
        boolean front = rider.squaredDistanceTo(getPos().add(Seat.PULLER.offset.rotateY(
            -getYaw() * MathHelper.RADIANS_PER_DEGREE))) < 1;
        for (double side : new double[]{1, -1}) {
            for (double dy : new double[]{0, .5, 1}) {
                Vec3d point = new Vec3d(side * (front ? .94 : 1.64), dy, front ? 2.1 : 0)
                    .rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE).add(getPos());
                Box box = rider.getDimensions(EntityPose.STANDING).getBoxAt(point);
                if (getWorld().isSpaceEmpty(rider, box) &&
                    getWorld().getBlockCollisions(rider, box.offset(0, -.22, 0)).iterator().hasNext()) return point;
            }
        }
        return super.updatePassengerForDismount(rider);
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        return hoodRaised() ? RAISED : FOLDED;
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (HOOD.equals(data)) calculateDimensions();
    }

    @Override public boolean canHit() { return !isRemoved(); }
    @Override public boolean isCollidable() { return !isRemoved(); }
    @Override public boolean collidesWith(Entity other) {
        return (other.isCollidable() || other.isPushable()) && !isConnectedThroughVehicle(other);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || isRemoved()) return false;
        boolean explosion = source.isIn(DamageTypeTags.IS_EXPLOSION);
        PlayerEntity player = source.getAttacker() instanceof PlayerEntity attacker ? attacker : null;
        if (!explosion && (player == null || !player.isSneaking() || hasPassengers())) return false;
        if (!getWorld().isClient) {
            removeAllPassengers();
            if ((player == null || !player.isCreative()) && getWorld().getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
                dropItem(NightWatchCarriage.CARRIAGE_ITEM);
            }
            discard();
        }
        return true;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound tag) {
        if (tag.contains("HoodRaised")) dataTracker.set(HOOD, tag.getBoolean("HoodRaised"));
        dataTracker.set(LAMPS, tag.getBoolean("LanternsOn"));
        restoredSeats.clear();
        for (Seat seat : Seat.values()) {
            if (tag.containsUuid("Seat_" + seat.key)) restoredSeats.put(seat, tag.getUuid("Seat_" + seat.key));
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound tag) {
        tag.putBoolean("HoodRaised", hoodRaised());
        tag.putBoolean("LanternsOn", lanternsOn());
        for (Entity passenger : getPassengerList()) {
            Seat seat = seatOf(passenger);
            if (seat != null) tag.putUuid("Seat_" + seat.key, passenger.getUuid());
        }
    }
}
