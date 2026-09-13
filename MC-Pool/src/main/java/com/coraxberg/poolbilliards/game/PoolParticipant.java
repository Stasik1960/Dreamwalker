package com.coraxberg.poolbilliards.game;

import net.minecraft.nbt.NbtCompound;

public class PoolParticipant {
    public String uuid;
    public String name;
    public int score;
    public boolean eliminated;

    public PoolParticipant(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Uuid", uuid);
        nbt.putString("Name", name);
        nbt.putInt("Score", score);
        nbt.putBoolean("Eliminated", eliminated);
        return nbt;
    }

    public static PoolParticipant fromNbt(NbtCompound nbt) {
        PoolParticipant participant = new PoolParticipant(nbt.getString("Uuid"), nbt.getString("Name"));
        participant.score = nbt.getInt("Score");
        participant.eliminated = nbt.getBoolean("Eliminated");
        return participant;
    }
}
