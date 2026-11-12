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
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import static dev.nightshift.skate.SkateControls.*;

public final class SkateboardEntity extends Entity {
    public static final int TRICK_DURATION = 8;
    public static final int PUSH_CYCLE_TICKS = 14;
    public static final double MAX_SPEED = 1.12;
    private static final double MAX_GRIND_SPEED = 1.40;
    private static final double GRIND_ACCELERATION = 0.035;
    private static final double STOP_SPEED = 0.018;
    private static final double GRAVITY = 0.065;
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

    private final SkateCombo combo = new SkateCombo();
    private ItemStack boardStack = ItemStack.EMPTY;
    private int controls;
    private int lastInputTick = -20;
    private int pendingTrick;
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
    }

    public int getTrickId() {
        return dataTracker.get(TRICK_ID);
    }

    public int getTrickStartAge() {
        return dataTracker.get(TRICK_START);
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

    private void rememberBoard(ItemStack stack) {
        boardStack = stack.getItem() instanceof SkateboardItem ? stack.copyWithCount(1) : ItemStack.EMPTY;
        dataTracker.set(BOARD_STYLE, BoardStyle.fromStack(boardStack).networkId());
    }

    void setBoardStack(ItemStack stack) {
        rememberBoard(stack);
        setCustomName(boardStack.hasCustomName() ? boardStack.getName() : null);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        rememberBoard(ItemStack.fromNbt(nbt.getCompound("BoardItem")));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
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
        BoardStyle style = getStyle();
        return style.riderHeight() - (isGrinding() ? style.boardslideDrop() : 0);
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
            if (passenger instanceof ServerPlayerEntity rider) {
                combo.bank(rider);
            }
            clearControls();
            dataTracker.set(TRICK_ID, 0);
            dataTracker.set(CROUCHING, false);
            dataTracker.set(PUSHING, false);
            dataTracker.set(STEERING_LEAN, 0.0f);
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
        if (pressed(input, TRICK)) {
            if (pressed(input, CROUCH)) {
                pendingTrick = 5;
            } else if (pressed(input, BRAKE)) {
                pendingTrick = 4;
            } else {
                pendingTrick = switch (steer(input)) {
                    case -1 -> 2;
                    case 1 -> 3;
                    default -> 1;
                };
            }
        }
    }

    private void clearControls() {
        controls = 0;
        wasJumpHeld = false;
        jumpCharge = 0;
        pendingTrick = 0;
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
        boolean jumpHeld = pressed(controls, JUMP);
        boolean grounded = isOnGround();
        ticksSinceGround = grounded ? 0 : Math.min(ticksSinceGround + 1, 10);
        boolean crouching = pressed(controls, CROUCH) || (grounded && jumpHeld);
        dataTracker.set(CROUCHING, crouching);
        boolean pushing = rail == null && grounded && !crouching && pressed(controls, ACCELERATE) && !pressed(controls, BRAKE);
        int now = (int) getWorld().getTime();
        if (pushing && (!isPushing() || now - getPushCycleStart() >= PUSH_CYCLE_TICKS)) {
            dataTracker.set(PUSH_CYCLE_START, now);
        }
        dataTracker.set(PUSHING, pushing);
        float lean = MathHelper.lerp(0.32f, getSteeringLean(), rail == null ? steer(controls) : 0);
        dataTracker.set(STEERING_LEAN, Math.abs(lean) < 0.02f ? 0.0f : lean);

        if (rail != null) {
            tickGrinding(rider, jumpHeld);
        } else {
            tickRolling(rider, grounded, jumpHeld);
        }
        double speed = getVelocity().horizontalLength();
        dataTracker.set(RIDE_SPEED, (float) speed);
        if (rider instanceof ServerPlayerEntity serverRider) {
            combo.tick(serverRider, rail == null && isOnGround() && speed < STOP_SPEED);
        }
        wasJumpHeld = jumpHeld;
        pendingTrick = 0;
    }

    private void tickRolling(PlayerEntity rider, boolean grounded, boolean jumpHeld) {
        double speed = getVelocity().horizontalLength();
        double vertical = hasNoGravity() ? getVelocity().y : Math.max(-0.85, getVelocity().y - GRAVITY);
        if (grounded) {
            double friction = surfaceFriction();
            if (pressed(controls, BRAKE)) {
                speed = Math.max(0, speed - 0.075) * friction;
            } else if (pressed(controls, ACCELERATE) && speed <= MAX_SPEED) {
                double acceleration = MathHelper.lerp(speed / MAX_SPEED, 0.040, 0.020);
                double poweredFriction = MathHelper.lerp(0.30, 1.0, friction);
                speed = Math.min(MAX_SPEED, (speed + acceleration) * poweredFriction);
            } else {
                speed *= friction;
            }
            vertical = hasNoGravity() ? 0.0 : -GRAVITY;
        } else {
            speed *= 0.994;
        }
        if (isTouchingWater()) {
            speed *= 0.60;
            vertical = Math.max(-0.12, vertical * 0.55);
        }
        if (speed < 0.009) {
            speed = 0;
        }

        float turn = grounded ? MathHelper.lerp(Math.min(1.0f, (float) (speed / MAX_SPEED)), 11.0f, 3.4f) : 1.3f;
        setYaw(MathHelper.wrapDegrees(getYaw() + turn * getSteeringLean()));

        if (jumpHeld && grounded) {
            jumpCharge = Math.min(jumpCharge + 1, MAX_CHARGE);
        }
        boolean jumping = !jumpHeld && wasJumpHeld && jumpCharge > 0 && ticksSinceGround <= 2;
        if (jumping) {
            vertical = 0.33 + 0.165 * jumpCharge / MAX_CHARGE;
            fallDistance = 0;
            playBoardSound(SoundEvents.BLOCK_WOOD_HIT, 0.68f, 0.85f);
        }
        if (!jumpHeld) {
            jumpCharge = 0;
        }

        if (rider != null && (!grounded || jumping) && getTrickId() == 0 && pendingTrick != 0) {
            startTrick(pendingTrick);
        }
        Vec3d travel = direction().multiply(speed).add(0, vertical, 0);
        if (rider != null && !grounded && railCooldown == 0) {
            GrindRail.Entry entry = GrindRail.findEntry(getWorld(), getPos(), travel);
            if (entry != null && startGrinding(entry, travel, rider)) {
                return;
            }
        }
        moveBoard(rider, travel);
        if (!grounded && isOnGround()) {
            boolean clean = finishTrick(rider);
            if (!clean) {
                setVelocity(getVelocity().multiply(0.56, 1, 0.56));
            }
            playBoardSound(SoundEvents.BLOCK_WOOD_HIT, 0.52f, 1.24f);
        } else if (rider != null && grounded && speed > 0.14 && age % (speed > 0.78 ? 3 : 5) == 0) {
            playBoardSound(SoundEvents.BLOCK_STONE_STEP, (float) (0.16 + speed * 0.32), (float) (1.02 + speed * 0.46));
        }
    }

    private double surfaceFriction() {
        var state = getWorld().getBlockState(getVelocityAffectingPos());
        if (state.isIn(BlockTags.SAND) || state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.SNOW_BLOCK)) {
            return 0.86;
        }
        if (state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.WOOL) || state.isOf(Blocks.SNOW)) {
            return 0.92;
        }
        return state.isIn(BlockTags.ICE) ? 0.994 : 0.979;
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
        Box riderBox = rider.getDimensions(EntityPose.STANDING).getBoxAt(boardPosition.add(0, getStyle().riderHeight(), 0));
        return !getWorld().getBlockCollisions(rider, riderBox).iterator().hasNext();
    }

    private Vec3d direction() {
        float angle = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(angle), 0, MathHelper.cos(angle));
    }

    private void startTrick(int trick) {
        dataTracker.set(TRICK_START, (int) getWorld().getTime());
        dataTracker.set(TRICK_ID, trick);
    }

    private boolean finishTrick(PlayerEntity rider) {
        int trick = getTrickId();
        if (trick == 0) {
            return true;
        }
        boolean clean = (int) getWorld().getTime() - getTrickStartAge() >= TRICK_DURATION;
        dataTracker.set(TRICK_ID, 0);
        if (clean && rider instanceof ServerPlayerEntity serverRider) {
            combo.add(serverRider, switch (trick) {
                case 1 -> 100;
                case 2, 3 -> 200;
                case 4 -> 300;
                case 5 -> 350;
                default -> throw new IllegalStateException("Unknown skate trick: " + trick);
            });
        }
        if (rider != null) {
            rider.sendMessage(Text.translatable("night_shift_skate.trick." + (clean ? trick : "bail")), true);
        }
        return clean;
    }

    private boolean startGrinding(GrindRail.Entry entry, Vec3d travel, PlayerEntity rider) {
        GrindRail candidate = entry.rail();
        Vec3d entryPoint = candidate.point(entry.along());
        Vec3d snap = entryPoint.subtract(getPos());
        Vec3d allowed = Entity.adjustMovementForCollisions(this, snap, getBoundingBox(), getWorld(), List.of());
        if (allowed.squaredDistanceTo(snap) > 0.0001
                || !riderFits(rider, entryPoint.add(0, -getStyle().boardslideDrop(), 0))
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
        rider.sendMessage(Text.translatable("night_shift_skate.grind.start"), true);
        playBoardSound(SoundEvents.BLOCK_CHAIN_STEP, 0.75f, 1.45f);
        return true;
    }

    private void tickGrinding(PlayerEntity rider, boolean jumpHeld) {
        if (rider == null) {
            stopGrinding(null, false);
            return;
        }
        railTicks++;
        railSpeed = pressed(controls, BRAKE) ? railSpeed * 0.90 : Math.min(MAX_GRIND_SPEED, railSpeed + GRIND_ACCELERATION);
        balance += 0.021f * ((getId() & 1) == 0 ? 1 : -1)
                + MathHelper.sin(age * 0.18f) * 0.015f + steer(controls) * 0.064f;
        dataTracker.set(GRIND_LEAN, MathHelper.clamp(balance, -1.0f, 1.0f));
        if (jumpHeld && !wasJumpHeld) {
            stopGrinding(rider, true);
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
                rider.sendMessage(Text.translatable("night_shift_skate.grind.balance_lost"), true);
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
            playBoardSound(SoundEvents.BLOCK_CHAIN_STEP, 0.42f, 1.22f);
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
        if (cleanExit && rider != null && railPointsAwarded > 0) {
            rider.sendMessage(Text.translatable("night_shift_skate.grind.finish"), true);
        }
    }

    private void playBoardSound(SoundEvent sound, float volume, float pitch) {
        getWorld().playSound(null, getX(), getY(), getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
    }
}
