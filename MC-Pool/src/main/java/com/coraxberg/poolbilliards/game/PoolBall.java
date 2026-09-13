package com.coraxberg.poolbilliards.game;

import net.minecraft.nbt.NbtCompound;

public class PoolBall {
    public int id;
    public double x;
    public double y;
    public double vx;
    public double vy;
    public boolean pocketed;

    public PoolBall(int id, double x, double y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public PoolBall copy() {
        PoolBall b = new PoolBall(id, x, y);
        b.vx = vx;
        b.vy = vy;
        b.pocketed = pocketed;
        return b;
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("id", id);
        n.putDouble("x", x);
        n.putDouble("y", y);
        n.putDouble("vx", vx);
        n.putDouble("vy", vy);
        n.putBoolean("pocketed", pocketed);
        return n;
    }

    public void fromNbt(NbtCompound n) {
        id = n.getInt("id");
        x = n.getDouble("x");
        y = n.getDouble("y");
        vx = n.getDouble("vx");
        vy = n.getDouble("vy");
        pocketed = n.getBoolean("pocketed");
    }
}
