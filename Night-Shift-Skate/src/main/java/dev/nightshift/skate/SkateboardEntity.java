package dev.nightshift.skate;

import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Dismounting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import static dev.nightshift.skate.SkateControls.*;

public final class SkateboardEntity extends Entity {
    private static final int GRAB_DURATION = 8;
    public static final int PUSH_CYCLE_TICKS = 14;
    public static final double MAX_SPEED = 1.12;
    private static final double MAX_GRIND_SPEED = 1.40;
    private static final double GRIND_ACCELERATION = 0.035;
    private static final double STOP_SPEED = 0.018;
    private static final double LOW_COAST_SPEED = 0.12;
    private static final double LOW_SPEED_ROLLING_RESISTANCE = 0.0014;
    private static final double MIN_MANUAL_SPEED = 0.12;
    private static final double MIN_WALL_SPEED = 0.23;
    private static final int WALL_RIDE_TICKS = 14;
    private static final double GRAVITY = 0.065;
    private static final double RAMP_STEP_LENGTH = 0.18;
    private static final double RAMP_MAX_STEP = 0.31;
    private static final double RAMP_CONTACT_EPSILON = 0.025;
    private static final int MAX_CHARGE = 9;

    private static final TrackedData<Integer> TRICK_ID = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> TRICK_START = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> GRINDING = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> GRIND_LEAN = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> CROUCHING = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> PUSHING = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> STEERING_LEAN = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> RIDE_SPEED = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> PUSH_CYCLE_START = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Byte> BOARD_STYLE = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Boolean> AIRBORNE = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> OLLIE_START = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> MANUAL_PITCH = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> WALL_RIDE_LEAN = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> BOARD_SETUP = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> DECK_REVERSED = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> RAMP_PITCH = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> RAMP_DROP = DataTracker.registerData(SkateboardEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private final SkateCombo combo = new SkateCombo();
    private ItemStack boardStack = ItemStack.EMPTY;
    private int controls;
    private int lastInputTick = -20;
    private int pendingAction;
    private int jumpCharge;
    private boolean wasJumpHeld;
    private int ticksSinceGround = 10;
    private GrindRail rail;
    private int railDirection;
    private double railSpeed;
    private float balance;
    private int railCooldown;
    private int railTicks;
    private double railDistance;
    private int railPointsAwarded;
    private int manualDirection;
    private double manualDistance;
    private int manualPointsAwarded;
    private Direction wallFace;
    private int wallSide;
    private int wallTicks;
    private int wallCooldown;

    private Vec3d interpolationTarget = Vec3d.ZERO;
    private float interpolationYaw;
    private int interpolationTicks;

    public SkateboardEntity(EntityType<? extends SkateboardEntity> type, World world) {
        super(type, world);
        setStepHeight(0.50f);
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(TRICK_ID, 0);
        dataTracker.startTracking(TRICK_START, 0);
        dataTracker.startTracking(GRINDING, false);
        dataTracker.startTracking(GRIND_LEAN, 0.0f);
        dataTracker.startTracking(CROUCHING, false);
        dataTracker.startTracking(PUSHING, false);
        dataTracker.startTracking(STEERING_LEAN, 0.0f);
        dataTracker.startTracking(RIDE_SPEED, 0.0f);
        dataTracker.startTracking(PUSH_CYCLE_START, 0);
        dataTracker.startTracking(BOARD_STYLE, BoardStyle.NIGHT_SHIFT.networkId());
        dataTracker.startTracking(AIRBORNE, false);
        dataTracker.startTracking(OLLIE_START, Integer.MIN_VALUE);
        dataTracker.startTracking(MANUAL_PITCH, 0.0f);
        dataTracker.startTracking(WALL_RIDE_LEAN, 0.0f);
        dataTracker.startTracking(BOARD_SETUP, BoardSetup.DEFAULT.packed());
        dataTracker.startTracking(DECK_REVERSED, false);
        dataTracker.startTracking(RAMP_PITCH, 0.0f);
        dataTracker.startTracking(RAMP_DROP, 0.0f);
    }

    public int getTrickId() {
        return dataTracker.get(TRICK_ID);
    }

    public SkateTrick getTrick() {
        return SkateTrick.fromId(getTrickId());
    }

    public int getTrickStartAge() {
        return dataTracker.get(TRICK_START);
    }

    public boolean isAirborne() {
        return dataTracker.get(AIRBORNE);
    }

    public int getOllieStartAge() {
        return dataTracker.get(OLLIE_START);
    }

    public float getManualPitch() {
        return dataTracker.get(MANUAL_PITCH);
    }

    public float getWallRideLean() {
        return dataTracker.get(WALL_RIDE_LEAN);
    }

    public boolean isDeckReversed() {
        return dataTracker.get(DECK_REVERSED);
    }

    public float getRampPitch() {
        return dataTracker.get(RAMP_PITCH);
    }

    public float getRampDrop() {
        return dataTracker.get(RAMP_DROP);
    }

    public boolean isGrinding() {
        return dataTracker.get(GRINDING);
    }

    public float getGrindLean() {
        return dataTracker.get(GRIND_LEAN);
    }

    public boolean isCrouching() {
        return dataTracker.get(CROUCHING);
    }

    public boolean isPushing() {
        return dataTracker.get(PUSHING);
    }

    public float getSteeringLean() {
        return dataTracker.get(STEERING_LEAN);
    }

    public float getRideSpeed() {
        return dataTracker.get(RIDE_SPEED);
    }

    public int getPushCycleStart() {
        return dataTracker.get(PUSH_CYCLE_START);
    }

    public BoardStyle getStyle() {
        return BoardStyle.fromNetworkId(dataTracker.get(BOARD_STYLE));
    }

    public BoardSetup getSetup() {
        return BoardSetup.fromPacked(dataTracker.get(BOARD_SETUP));
    }

    public int getTrickDuration() {
        return getTrick().isGrab() ? GRAB_DURATION : getSetup().deck().flipTicks();
    }

    private void rememberBoard(ItemStack stack) {
        boardStack = stack.getItem() instanceof SkateboardItem ? stack.copyWithCount(1) : ItemStack.EMPTY;
        dataTracker.set(BOARD_STYLE, BoardStyle.fromStack(boardStack).networkId());
        dataTracker.set(BOARD_SETUP, BoardSetup.fromStack(boardStack).packed());
    }

    void setBoardStack(ItemStack stack) {
        rememberBoard(stack);
        setCustomName(boardStack.hasCustomName() ? boardStack.getName() : null);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        rememberBoard(ItemStack.fromNbt(nbt.getCompound("BoardItem")));
        dataTracker.set(DECK_REVERSED, nbt.getBoolean("DeckReversed"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putBoolean("DeckReversed", isDeckReversed());
        if (!boardStack.isEmpty()) {
            nbt.put("BoardItem", boardStack.writeNbt(new NbtCompound()));
        }
    }

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public float getTargetingMargin() {
        return 0.12f;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof PlayerEntity && !hasPassengers();
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (passenger instanceof ServerPlayerEntity rider) {
            combo.sync(rider);
        }
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        if (hasPassenger(passenger)) {
            positionUpdater.accept(passenger, getX(), getY() + riderHeight(), getZ());
        }
    }

    private double riderHeight() {
        BoardSetup setup = getSetup();
        return setup.riderHeight() - (isGrinding() ? setup.deckBase() : getRampDrop());
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        Vec3d heading = direction();
        Vec3d side = new Vec3d(heading.z, 0, -heading.x);
        for (Vec3d offset : List.of(side, side.negate(), heading.negate(), heading)) {
            Vec3d base = getPos().add(offset.multiply(0.78));
            for (double height : new double[]{0.0, 0.5, -0.5}) {
                Vec3d feet = base.add(0, height, 0);
                Box standing = passenger.getDimensions(EntityPose.STANDING).getBoxAt(feet);
                if (Dismounting.canPlaceEntityAt(getWorld(), passenger, standing)
                        && getWorld().getBlockCollisions(passenger, standing.offset(0, -0.60, 0)).iterator().hasNext()) {
                    return feet;
                }
            }
        }
        return super.updatePassengerForDismount(passenger);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!getWorld().isClient) {
            if (rail != null) {
                stopGrinding(passenger instanceof PlayerEntity player ? player : null, false);
            }
            finishManual(passenger instanceof PlayerEntity player ? player : null);
            if (passenger instanceof ServerPlayerEntity rider) {
                combo.bank(rider);
            }
            clearControls();
            stopWallRide();
            dataTracker.set(TRICK_ID, 0);
            dataTracker.set(CROUCHING, false);
            dataTracker.set(PUSHING, false);
            dataTracker.set(STEERING_LEAN, 0.0f);
            dataTracker.set(AIRBORNE, false);
            dataTracker.set(OLLIE_START, Integer.MIN_VALUE);
            dataTracker.set(MANUAL_PITCH, 0.0f);
            dataTracker.set(WALL_RIDE_LEAN, 0.0f);
        }
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (isRemoved()) {
            return ActionResult.PASS;
        }
        if (getFirstPassenger() == player) {
            if (player.isSneaking() || isCrouching()) {
                if (!getWorld().isClient) {
                    player.stopRiding();
                }
                return ActionResult.success(getWorld().isClient);
            }
            return ActionResult.PASS;
        }
        if (hasPassengers() || player.hasVehicle()) {
            return ActionResult.PASS;
        }
        if (player.isSneaking()) {
            if (!getWorld().isClient) {
                ItemStack stack = boardStack.isEmpty() ? new ItemStack(NightShiftSkate.SKATEBOARD) : boardStack.copy();
                if (!player.getAbilities().creativeMode || !player.getInventory().contains(stack)) {
                    if (!player.getInventory().insertStack(stack)) {
                        player.dropItem(stack, false);
                    }
                }
                discard();
            }
            return ActionResult.success(getWorld().isClient);
        }
        if (getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (!riderFits(player, getPos()) || !player.startRiding(this, true)) {
            return ActionResult.PASS;
        }
        if (getVelocity().horizontalLength() < 0.06) {
            setYaw(MathHelper.wrapDegrees(player.getYaw()));
        }
        return ActionResult.CONSUME;
    }

    void acceptControls(int input) {
        controls = input & CONTINUOUS;
        lastInputTick = age;
        if ((input & (TRICK | GRAB)) != 0) {
            pendingAction = input;
        }
    }

    private void clearControls() {
        controls = 0;
        wasJumpHeld = false;
        jumpCharge = 0;
        pendingAction = 0;
        lastInputTick = age - 20;
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch,
                                              int interpolationSteps, boolean interpolate) {
        interpolationTarget = new Vec3d(x, y, z);
        interpolationYaw = yaw;
        interpolationTicks = interpolate ? 2 : Math.max(1, Math.min(interpolationSteps, 2));
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            if (interpolationTicks > 0) {
                double fraction = 1.0 / interpolationTicks--;
                Vec3d next = getPos().lerp(interpolationTarget, fraction);
                setPosition(next.x, next.y, next.z);
                setYaw(MathHelper.lerpAngleDegrees((float) fraction, getYaw(), interpolationYaw));
            }
            return;
        }

        PlayerEntity rider = getFirstPassenger() instanceof PlayerEntity player ? player : null;
        if (rider == null || age - lastInputTick > 12) {
            clearControls();
        }
        if (railCooldown > 0) {
            railCooldown--;
        }
        if (wallCooldown > 0) {
            wallCooldown--;
        }
        boolean jumpHeld = pressed(controls, JUMP);
        boolean grounded = isOnGround();
        ticksSinceGround = grounded ? 0 : Math.min(ticksSinceGround + 1, 10);
        boolean crouching = pressed(controls, CROUCH) || (grounded && jumpHeld);
        dataTracker.set(CROUCHING, crouching);
        boolean pushing = rail == null && grounded && !crouching && manualDirection == 0
                && !pressed(pendingAction, GRAB) && pressed(controls, ACCELERATE) && !pressed(controls, BRAKE);
        int now = (int) getWorld().getTime();
        if (pushing && (!isPushing() || now - getPushCycleStart() >= PUSH_CYCLE_TICKS)) {
            dataTracker.set(PUSH_CYCLE_START, now);
        }
        dataTracker.set(PUSHING, pushing);
        float lean = MathHelper.lerp(0.32f, getSteeringLean(), rail == null ? steer(controls) : 0);
        dataTracker.set(STEERING_LEAN, Math.abs(lean) < 0.02f ? 0.0f : lean);

        if (rail != null) {
            tickGrinding(rider, jumpHeld);
        } else if (wallFace == null || !tickWallRide(rider, jumpHeld)) {
            tickRolling(rider, grounded, jumpHeld);
        }
        double speed = getVelocity().horizontalLength();
        dataTracker.set(RIDE_SPEED, (float) speed);
        updateRampPose();
        boolean airborne = rider != null && !isOnGround() && rail == null;
        if (!airborne) {
            dataTracker.set(OLLIE_START, Integer.MIN_VALUE);
        }
        dataTracker.set(AIRBORNE, airborne);
        updateManual(rider);
        float wallLean = MathHelper.lerp(0.42f, getWallRideLean(), wallSide);
        dataTracker.set(WALL_RIDE_LEAN, Math.abs(wallLean) < 0.015f ? 0.0f : wallLean);
        if (rider instanceof ServerPlayerEntity serverRider) {
            combo.tick(serverRider, rail == null && isOnGround() && speed < STOP_SPEED);
        }
        wasJumpHeld = jumpHeld;
        pendingAction = 0;
    }

    private void tickRolling(PlayerEntity rider, boolean grounded, boolean jumpHeld) {
        double speed = getVelocity().horizontalLength();
        double vertical = hasNoGravity() ? getVelocity().y : Math.max(-0.85, getVelocity().y - GRAVITY);
        boolean jumping = !jumpHeld && wasJumpHeld && jumpCharge > 0 && ticksSinceGround <= 2;
        RampSurface ramp = grounded ? RampSurface.under(getWorld(), getBoundingBox(), getY() - 0.05, getY() + 0.14) : null;
        if (rider == null || !grounded || jumpHeld || jumping || speed < MIN_MANUAL_SPEED || pressed(controls, BRAKE)) {
            finishManual(rider);
        } else if (pressed(pendingAction, GRAB)) {
            toggleManual(rider);
        }
        BoardSetup setup = getSetup();
        double topSpeed = setup.topSpeed();
        if (grounded) {
            RollSurface surface = ramp == null ? rollSurface() : RollSurface.FIRM;
            if (pressed(controls, BRAKE)) {
                speed = Math.max(0, speed - 0.075) * surface.poweredFriction;
            } else if (pressed(controls, ACCELERATE) && speed <= topSpeed && manualDirection == 0) {
                double acceleration = MathHelper.lerp(speed / topSpeed, 0.040, 0.020) * setup.wheels().acceleration();
                double poweredFriction = MathHelper.lerp(0.30, 1.0, surface.poweredFriction);
                speed = Math.min(topSpeed, (speed + acceleration) * poweredFriction);
            } else {
                speed *= setup.coastRetention(surface.coastRetention);
                if (speed < LOW_COAST_SPEED) {
                    speed -= LOW_SPEED_ROLLING_RESISTANCE * (1.0 - speed / LOW_COAST_SPEED);
                }
            }
            if (ramp != null && !jumping && !pressed(controls, BRAKE)) {
                double grade = ramp.grade(direction());
                if (grade < 0) {
                    speed = Math.min(MAX_GRIND_SPEED, speed - grade * 0.043);
                } else {
                    speed = Math.max(0, speed - grade * 0.016);
                }
            }
            vertical = hasNoGravity() ? 0.0 : -GRAVITY;
        } else {
            speed *= 0.994;
        }
        if (isTouchingWater()) {
            speed *= 0.60;
            vertical = Math.max(-0.12, vertical * 0.55);
        }
        if (speed < STOP_SPEED) {
            speed = 0;
        }

        float turn = grounded ? (float) setup.trucks().steering()
                * MathHelper.lerp(Math.min(1.0f, (float) (speed / topSpeed)), 11.0f, 3.4f) : 1.3f;
        setYaw(MathHelper.wrapDegrees(getYaw() + turn * getSteeringLean()));

        if (jumpHeld && grounded) {
            jumpCharge = Math.min(jumpCharge + 1, MAX_CHARGE);
        }
        if (jumping) {
            vertical = (0.33 + 0.165 * jumpCharge / MAX_CHARGE) * setup.deck().pop();
            fallDistance = 0;
            dataTracker.set(OLLIE_START, (int) getWorld().getTime());
            playBoardSound(SoundEvents.BLOCK_WOOD_HIT, 0.68f, 0.85f);
        }
        if (!jumpHeld) {
            jumpCharge = 0;
        }

        if (rider != null && (!grounded || jumping) && getTrickId() == 0) {
            if (!grounded && pressed(pendingAction, GRAB) && startWallRide(rider, speed, jumpHeld)) {
                return;
            }
            SkateTrick trick = SkateTrick.select(pendingAction);
            if (trick != SkateTrick.NONE) {
                startTrick(trick);
            }
        }
        Vec3d travel = direction().multiply(speed).add(0, vertical, 0);
        if (rider != null && !grounded && railCooldown == 0) {
            GrindRail.Entry entry = GrindRail.findEntry(getWorld(), getPos(), travel);
            if (entry != null && startGrinding(entry, travel, rider)) {
                return;
            }
        }
        if (grounded && !jumping && (ramp != null || RampSurface.crosses(getWorld(), getBoundingBox(), travel))) {
            moveAcrossRamp(rider, travel, ramp);
        } else {
            moveBoard(rider, travel);
        }
        if (!grounded && isOnGround()) {
            boolean clean = finishTrick(rider);
            if (!clean) {
                setVelocity(getVelocity().multiply(0.56, 1, 0.56));
            }
            playBoardSound(SoundEvents.BLOCK_WOOD_HIT, 0.52f, 1.24f);
        }
    }

    private void moveAcrossRamp(PlayerEntity rider, Vec3d travel, RampSurface support) {
        Vec3d start = getPos();
        int steps = Math.max(1, MathHelper.ceil(travel.horizontalLength() / RAMP_STEP_LENGTH));
        Vec3d step = travel.multiply(1.0 / steps);
        double vertical = travel.y;

        for (int i = 0; i < steps; i++) {
            Box nextFootprint = getBoundingBox().offset(step.x, 0, step.z);
            RampSurface next = RampSurface.under(getWorld(), nextFootprint, getY() - RAMP_MAX_STEP, getY() + RAMP_MAX_STEP);
            if (next == null) {
                double grade = support == null ? 0 : support.grade(direction());
                if (grade > 0.08 && travel.horizontalLength() >= 0.24 && !hasLandingAt(nextFootprint)) {
                    vertical = Math.min(0.55, travel.horizontalLength() * grade * 0.78);
                    int stepsLeft = steps - i;
                    moveBoard(rider, new Vec3d(step.x * stepsLeft, vertical * stepsLeft / steps, step.z * stepsLeft));
                    if (getVelocity().y > 0) {
                        fallDistance = 0;
                        dataTracker.set(OLLIE_START, (int) getWorld().getTime());
                    } else {
                        vertical = 0;
                    }
                    break;
                }
                moveBoard(rider, step);
                support = null;
                continue;
            }

            // Rise before crossing a tread; descend after moving past the tread supporting us.
            moveBoard(rider, new Vec3d(step.x, Math.max(0, next.height() - getY()), step.z));
            support = RampSurface.under(getWorld(), getBoundingBox(), getY() - RAMP_MAX_STEP,
                    getY() + RAMP_CONTACT_EPSILON);
            if (support != null && support.height() <= getY()) {
                moveBoard(rider, new Vec3d(0, support.height() - getY(), 0));
            }
            if (support != null && Math.abs(getY() - support.height()) <= RAMP_CONTACT_EPSILON) {
                setOnGround(true);
                fallDistance = 0;
            }
        }
        Vec3d moved = getPos().subtract(start);
        setVelocity(moved.x, isOnGround() ? 0 : vertical, moved.z);
    }

    private boolean hasLandingAt(Box footprint) {
        double reach = 0.15;
        Vec3d allowed = Entity.adjustMovementForCollisions(this, new Vec3d(0, -reach, 0),
                footprint, getWorld(), List.of());
        return allowed.y > -reach + 0.0001;
    }

    private void updateRampPose() {
        RampSurface ramp = isOnGround() && rail == null
                ? RampSurface.under(getWorld(), getBoundingBox(), getY() - 0.05, getY() + 0.14) : null;
        float targetPitch = ramp == null ? 0 : (float) (Math.atan(ramp.grade(direction())) * MathHelper.DEGREES_PER_RADIAN);
        float pitch = MathHelper.lerp(0.56f, getRampPitch(), targetPitch);
        dataTracker.set(RAMP_PITCH, Math.abs(pitch) < 0.05f ? 0.0f : pitch);
        float drop = ramp == null ? 0 : (float) Math.max(0, getY() - ramp.centerHeight());
        dataTracker.set(RAMP_DROP, drop);
    }

    private RollSurface rollSurface() {
        var state = getWorld().getBlockState(getVelocityAffectingPos());
        if (state.isIn(BlockTags.SAND) || state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.SNOW_BLOCK)) {
            return RollSurface.LOOSE;
        }
        if (state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.WOOL) || state.isOf(Blocks.SNOW)) {
            return RollSurface.SOFT;
        }
        return state.isIn(BlockTags.ICE) ? RollSurface.ICE : RollSurface.FIRM;
    }

    private enum RollSurface {
        LOOSE(0.86, 0.990),
        SOFT(0.92, 0.9965),
        FIRM(0.979, BoardSetup.FIRM_COAST_RETENTION),
        ICE(0.994, 0.9992);

        private final double poweredFriction;
        private final double coastRetention;

        RollSurface(double poweredFriction, double coastRetention) {
            this.poweredFriction = poweredFriction;
            this.coastRetention = coastRetention;
        }
    }

    private void toggleManual(PlayerEntity rider) {
        if (manualDirection != 0) {
            finishManual(rider);
            return;
        }
        manualDirection = pressed(pendingAction, ACCELERATE) ? -1 : 1;
        manualDistance = 0;
        manualPointsAwarded = 0;
    }

    private void finishManual(PlayerEntity rider) {
        if (manualDirection == 0) return;
        SkateCombo.announce(rider, manualDirection > 0 ? SkateTrick.MANUAL : SkateTrick.NOSE_MANUAL,
                manualPointsAwarded);
        manualDirection = 0;
    }

    private void updateManual(PlayerEntity rider) {
        double speed = getVelocity().horizontalLength();
        if (!isOnGround() || speed < MIN_MANUAL_SPEED || isTouchingWater()) {
            finishManual(rider);
        }
        float pitch = MathHelper.lerp(0.34f, getManualPitch(), manualDirection * 10.0f);
        dataTracker.set(MANUAL_PITCH, Math.abs(pitch) < 0.05f ? 0.0f : pitch);
        if (manualDirection == 0 || !(rider instanceof ServerPlayerEntity serverRider)) {
            return;
        }
        manualDistance += speed;
        if (manualDistance >= 1.4 && age % 5 == 0) {
            int earned = 70 + (int) (manualDistance * 14);
            combo.add(serverRider, earned - manualPointsAwarded);
            manualPointsAwarded = earned;
        }
    }

    private boolean startWallRide(PlayerEntity rider, double speed, boolean jumpHeld) {
        int side = steer(pendingAction);
        if (wallCooldown > 0 || side == 0 || speed < MIN_WALL_SPEED || isTouchingWater()) {
            return false;
        }
        Vec3d heading = direction();
        Vec3d towardWall = new Vec3d(-heading.z, 0, heading.x).multiply(side);
        BlockHitResult wall = findWall(towardWall);
        if (wall == null || Math.abs(heading.dotProduct(Vec3d.of(wall.getSide().getVector()))) > 0.55) {
            return false;
        }
        wallFace = wall.getSide();
        wallSide = side;
        wallTicks = 0;
        fallDistance = 0;
        return tickWallRide(rider, jumpHeld);
    }

    private boolean tickWallRide(PlayerEntity rider, boolean jumpHeld) {
        Vec3d normal = Vec3d.of(wallFace.getVector());
        double speed = getVelocity().horizontalLength();
        BlockHitResult wall = findWall(normal.negate());
        if (rider == null || isOnGround() || isTouchingWater() || speed < MIN_WALL_SPEED
                || wallTicks >= WALL_RIDE_TICKS || wall == null || wall.getSide() != wallFace) {
            stopWallRide();
            return false;
        }
        Vec3d heading = direction();
        Vec3d tangent = heading.subtract(normal.multiply(heading.dotProduct(normal))).normalize();
        if (jumpHeld && !wasJumpHeld) {
            stopWallRide();
            Vec3d exit = tangent.multiply(speed).add(normal.multiply(0.28));
            setYaw((float) Math.toDegrees(Math.atan2(-exit.x, exit.z)));
            dataTracker.set(OLLIE_START, (int) getWorld().getTime());
            moveBoard(rider, exit.add(0, 0.36, 0));
            return true;
        }
        double clearance = getPos().subtract(wall.getPos()).dotProduct(normal);
        double correction = MathHelper.clamp(0.42 - clearance, -0.22, 0.22);
        double vertical = wallTicks == 0 ? 0.13 : Math.max(-0.24, getVelocity().y - 0.028);
        setYaw((float) Math.toDegrees(Math.atan2(-tangent.x, tangent.z)));
        moveBoard(rider, tangent.multiply(speed * 0.98).add(normal.multiply(correction)).add(0, vertical, 0));
        if (++wallTicks == 3 && rider instanceof ServerPlayerEntity serverRider) {
            combo.add(serverRider, SkateTrick.WALLRIDE);
        }
        return true;
    }

    private BlockHitResult findWall(Vec3d towardWall) {
        Vec3d start = getPos().add(0, 0.25, 0);
        Vec3d reach = towardWall.multiply(0.78);
        BlockHitResult lower = traceWall(start, reach);
        if (lower.getType() != HitResult.Type.BLOCK || lower.getSide().getAxis() == Direction.Axis.Y) {
            return null;
        }
        BlockHitResult upper = traceWall(start.add(0, 0.80, 0), reach);
        if (upper.getType() != HitResult.Type.BLOCK || upper.getSide() != lower.getSide()) {
            return null;
        }
        Vec3d normal = Vec3d.of(lower.getSide().getVector());
        return Math.abs(upper.getPos().subtract(lower.getPos()).dotProduct(normal)) <= 0.04 ? lower : null;
    }

    private BlockHitResult traceWall(Vec3d start, Vec3d reach) {
        return getWorld().raycast(new RaycastContext(start, start.add(reach),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
    }

    private void stopWallRide() {
        if (wallFace != null) {
            wallFace = null;
            wallSide = 0;
            wallCooldown = 14;
        }
    }

    private void moveBoard(PlayerEntity rider, Vec3d travel) {
        Vec3d oldPosition = getPos();
        Vec3d permitted = travel;
        if (rider != null) {
            Box standing = rider.getDimensions(EntityPose.STANDING).getBoxAt(oldPosition.add(0, riderHeight(), 0));
            Box upperBody = new Box(standing.minX, standing.minY + 0.51, standing.minZ,
                    standing.maxX, standing.maxY, standing.maxZ);
            permitted = Entity.adjustMovementForCollisions(this, travel, upperBody, getWorld(), List.of());
        }
        move(MovementType.SELF, permitted);
        Vec3d moved = getPos().subtract(oldPosition);
        double vy = isOnGround() || (travel.y > 0 && moved.y < travel.y - 0.002) ? 0 : travel.y;
        setVelocity(moved.x, vy, moved.z);
    }

    private boolean riderFits(PlayerEntity rider, Vec3d boardPosition) {
        Box riderBox = rider.getDimensions(EntityPose.STANDING).getBoxAt(boardPosition.add(0, getSetup().riderHeight(), 0));
        return !getWorld().getBlockCollisions(rider, riderBox).iterator().hasNext();
    }

    private Vec3d direction() {
        float angle = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(angle), 0, MathHelper.cos(angle));
    }

    private void startTrick(SkateTrick trick) {
        dataTracker.set(TRICK_START, (int) getWorld().getTime());
        dataTracker.set(TRICK_ID, trick.ordinal());
    }

    private boolean finishTrick(PlayerEntity rider) {
        SkateTrick trick = getTrick();
        if (trick == SkateTrick.NONE) {
            return true;
        }
        boolean clean = (int) getWorld().getTime() - getTrickStartAge() >= getTrickDuration();
        dataTracker.set(TRICK_ID, 0);
        if (clean && trick.reversesDeck()) {
            dataTracker.set(DECK_REVERSED, !isDeckReversed());
        }
        if (clean && rider instanceof ServerPlayerEntity serverRider) {
            combo.add(serverRider, trick);
        }
        return clean;
    }

    private boolean startGrinding(GrindRail.Entry entry, Vec3d travel, PlayerEntity rider) {
        GrindRail candidate = entry.rail();
        Vec3d entryPoint = candidate.point(entry.along());
        Vec3d snap = entryPoint.subtract(getPos());
        Vec3d allowed = Entity.adjustMovementForCollisions(this, snap, getBoundingBox(), getWorld(), List.of());
        if (allowed.squaredDistanceTo(snap) > 0.0001
                || !riderFits(rider, entryPoint.add(0, -getSetup().deckBase(), 0))
                || !getWorld().isSpaceEmpty(this, getBoundingBox().offset(snap))) {
            return false;
        }
        boolean clean = finishTrick(rider);
        rail = candidate;
        railDirection = candidate.along(travel) < 0 ? -1 : 1;
        railSpeed = travel.horizontalLength() * (clean ? 1.0 : 0.70);
        railTicks = 0;
        railDistance = 0;
        railPointsAwarded = 0;
        balance = 0;
        setPosition(entryPoint.x, entryPoint.y, entryPoint.z);
        float yaw = candidate.axis() == Direction.Axis.X ? -90.0f * railDirection : (railDirection > 0 ? 0 : 180);
        setYaw(yaw);
        setVelocity(direction().multiply(railSpeed));
        setOnGround(false);
        fallDistance = 0;
        dataTracker.set(GRINDING, true);
        playBoardSound(NightShiftSkate.SKATE_GRIND, 0.70f, 1.08f);
        return true;
    }

    private void tickGrinding(PlayerEntity rider, boolean jumpHeld) {
        if (rider == null) {
            stopGrinding(null, false);
            return;
        }
        railTicks++;
        railSpeed = pressed(controls, BRAKE) ? railSpeed * 0.90 : Math.min(MAX_GRIND_SPEED, railSpeed + GRIND_ACCELERATION);
        float balanceDrift = 0.021f * ((getId() & 1) == 0 ? 1 : -1) + MathHelper.sin(age * 0.18f) * 0.015f;
        balance += balanceDrift / (float) getSetup().trucks().balance() + steer(controls) * 0.064f;
        dataTracker.set(GRIND_LEAN, MathHelper.clamp(balance, -1.0f, 1.0f));
        if (jumpHeld && !wasJumpHeld) {
            stopGrinding(rider, true);
            dataTracker.set(OLLIE_START, (int) getWorld().getTime());
            setVelocity(direction().multiply(railSpeed).add(0, 0.38, 0));
            moveBoard(rider, getVelocity());
            playBoardSound(SoundEvents.BLOCK_WOOD_HIT, 0.63f, 1.0f);
            return;
        }
        boolean lostBalance = Math.abs(balance) >= 1.0f;
        if (lostBalance || railSpeed < 0.075) {
            stopGrinding(rider, false);
            Vec3d forward = direction();
            double sideSpeed = Math.copySign(Math.max(0.23, railSpeed * 0.28), balance);
            Vec3d drift = new Vec3d(-forward.z, 0, forward.x).multiply(sideSpeed);
            Vec3d exitVelocity = forward.multiply(railSpeed).add(drift).add(0, 0.12, 0);
            setYaw((float) Math.toDegrees(Math.atan2(-exitVelocity.x, exitVelocity.z)));
            moveBoard(rider, exitVelocity);
            if (lostBalance) {
                rider.damage(getDamageSources().fall(), 2.0f);
            }
            return;
        }

        double along = rail.along(getPos()) + railDirection * railSpeed;
        GrindRail next = rail.ahead(getWorld(), along);
        Vec3d target = rail.point(along);
        Vec3d travel = target.subtract(getPos());
        moveBoard(rider, travel);
        if (next == null || getPos().squaredDistanceTo(target) > 0.0025) {
            stopGrinding(rider, true);
            return;
        }
        rail = next;
        railDistance += travel.horizontalLength();
        fallDistance = 0;
        if (railPointsAwarded == 0 || railTicks % 4 == 0) {
            awardGrindPoints(rider);
        }
        if (railTicks % 5 == 0) {
            playBoardSound(NightShiftSkate.SKATE_GRIND, (float) (0.30 + railSpeed * 0.16), (float) (0.92 + railSpeed * 0.22));
        }
    }

    private void awardGrindPoints(PlayerEntity rider) {
        if (rider instanceof ServerPlayerEntity serverRider && railDistance >= 0.60) {
            int earned = 120 + (int) (railDistance * 24);
            if (earned > railPointsAwarded) {
                combo.add(serverRider, earned - railPointsAwarded);
                railPointsAwarded = earned;
            }
        }
    }

    private void stopGrinding(PlayerEntity rider, boolean cleanExit) {
        awardGrindPoints(rider);
        rail = null;
        railCooldown = 9;
        dataTracker.set(GRINDING, false);
        dataTracker.set(GRIND_LEAN, 0.0f);
        if (cleanExit) {
            SkateCombo.announce(rider, SkateTrick.BOARDSLIDE, railPointsAwarded);
        }
    }

    private void playBoardSound(SoundEvent sound, float volume, float pitch) {
        getWorld().playSound(null, getX(), getY(), getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
    }
}
