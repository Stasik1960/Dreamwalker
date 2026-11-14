package dev.yharnamdice;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

public final class TableSeat extends Entity {
    private double exitX;
    private double exitZ;

    public TableSeat(EntityType<? extends TableSeat> type, World world) {
        super(type, world);
        setNoGravity(true);
        noClip = true;
    }

    public void place(double x, double y, double z, float yaw, double exitX, double exitZ) {
        refreshPositionAndAngles(x, y, z, yaw, 0);
        this.exitX = exitX;
        this.exitZ = exitZ;
    }

    @Override public void tick() {
        super.tick();
        if (!getWorld().isClient && !hasPassengers() && age > 3) discard();
    }

    @Override public double getMountedHeightOffset() { return .03; }

    @Override protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        super.updatePassengerPosition(passenger, updater);
        passenger.setBodyYaw(getYaw());
    }

    @Override public net.minecraft.util.math.Vec3d updatePassengerForDismount(net.minecraft.entity.LivingEntity passenger) {
        return new net.minecraft.util.math.Vec3d(exitX, getY() - .05, exitZ);
    }

    @Override protected void initDataTracker() {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
}
