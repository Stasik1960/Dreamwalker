package com.yharnam.casino;

import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Server-authoritative two-click wheel game. The wheel stores only phase/timing, never entities. */
public final class FortuneWheelEntity extends BlockEntity {
    private static final int CHARGE_TIMEOUT = 80;
    private static final int SPIN_TICKS = 80;
    private long chargeStarted = -1L;
    private long spinStarted = -1L;
    private long spinDuration;
    private float initialAngle;
    private float finalAngle;
    private float chargeStrength = -1.0F;
    private String result = "";
    private UUID chargeOwner;

    public FortuneWheelEntity(BlockPos pos, BlockState state) {
        super(YharnamCasino.WHEEL_ENTITY, pos, state);
    }

    /** First click begins the timing bar. Second click commits its current power. */
    public boolean use(ServerPlayerEntity player) {
        if (!(world instanceof ServerWorld server))
            return false;
        long now = server.getTime();
        if (active())
            advanceClock(server);
        if (spinStarted >= 0)
            return false;
        if (chargeStarted < 0) {
            chargeOwner = player.getUuid();
            chargeStarted = now;
            chargeStrength = -1;
            result = "";
            player.sendMessage(Text.literal("Шкала запущена. Нажмите ещё раз, чтобы крутануть колесо."), true);
        } else {
            if (!player.getUuid().equals(chargeOwner))
                return false;
            chargeStrength = strengthAt(now);
            chargeStarted = -1;
            initialAngle = normalize(finalAngle);
            spinDuration = SPIN_TICKS + Math.round(chargeStrength * 100);
            spinStarted = now;
        }
        markDirty();
        sync();
        schedule(server);
        return true;
    }

    private void schedule(ServerWorld server) {
        long due = chargeStarted >= 0 ? chargeStarted + CHARGE_TIMEOUT + 1 : spinStarted + spinDuration;
        server.scheduleBlockTick(pos, getCachedState().getBlock(), (int) Math.max(1, Math.min(10,due - server.getTime())));
    }

    void reset(ServerPlayerEntity player) {
        chargeStarted=spinStarted=-1;
        chargeStrength=-1;
        chargeOwner=null;
        initialAngle=finalAngle=0;
        result="";
        markDirty();sync();
        player.sendMessage(Text.literal("Колесо сброшено"),true);
    }

    void advanceClock(ServerWorld server) {
        long now = server.getTime();
        if (chargeStarted >= 0 && (now - chargeStarted > CHARGE_TIMEOUT || !GamingFurnitureBlock.inRange(server,pos,chargeOwner))) {
            chargeStarted = -1;
            chargeOwner = null;
            chargeStrength = -1;
        } else if (spinStarted >= 0 && now - spinStarted >= spinDuration) {
            finalAngle = normalize(initialAngle + travel());
            int sector = 1 + Math.floorMod(Math.round(finalAngle * 16 / (float) (Math.PI * 2)), 16);
            result = Integer.toString(sector);
            spinStarted = -1;
            var player = chargeOwner == null ? null : server.getServer().getPlayerManager().getPlayer(chargeOwner);
            if (player != null)
                player.sendMessage(Text.literal("Колесо остановилось на секторе " + sector), true);
            chargeOwner = null;
        } else {
            if(spinStarted>=0 && chargeOwner!=null && !GamingFurnitureBlock.inRange(server,pos,chargeOwner)) {
                chargeOwner=null;markDirty();
            }
            if (active())
                schedule(server);
            return;
        }
        markDirty();
        sync();
    }

    private float travel() {
        return (chargeStrength * 12 + 2) * (float) (Math.PI * 2);
    }

    private float strengthAt(double now) {
        float elapsed = Math.max(0.0F, Math.min(CHARGE_TIMEOUT, (float) (now - chargeStarted)));
        return .5F + .5F * (float) Math.sin(elapsed / CHARGE_TIMEOUT * Math.PI * 2.0 - Math.PI / 2.0);
    }

    public float angle(float tickDelta) {
        if (spinStarted < 0 || world == null)
            return normalize(finalAngle);
        float progress =
            Math.max(0.0F, Math.min(1.0F, (world.getTime() - spinStarted + tickDelta) / (float) spinDuration));
        float eased = 1.0F - (float) Math.pow(1.0F - progress, 3.0);
        return normalize(initialAngle + eased * travel());
    }

    public float strength(float tickDelta) {
        if (chargeStarted < 0 || world == null)
            return -1.0F;
        return strengthAt(world.getTime() + (double) tickDelta);
    }

    public String result() {
        return result;
    }
    public boolean active() {
        return chargeStarted >= 0 || spinStarted >= 0;
    }
    private static float normalize(float angle) {
        float full = (float) (Math.PI * 2.0);
        angle %= full;
        return angle < 0 ? angle + full : angle;
    }
    private void sync() {
        if (world instanceof ServerWorld server)
            server.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }
    private void writeTiming(NbtCompound nbt) {
        nbt.putLong("ChargeStarted", chargeStarted);
        nbt.putLong("SpinStarted", spinStarted);
        nbt.putLong("SpinDuration", spinDuration);
        nbt.putFloat("InitialAngle", initialAngle);
        nbt.putFloat("FinalAngle", finalAngle);
        nbt.putFloat("ChargeStrength", chargeStrength);
        nbt.putString("Result", result);
    }
    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        writeTiming(nbt);
        if (chargeOwner != null)
            nbt.putUuid("ChargeOwner", chargeOwner);
    }
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        chargeStarted = nbt.contains("ChargeStarted") ? nbt.getLong("ChargeStarted") : -1;
        spinStarted = nbt.contains("SpinStarted") ? nbt.getLong("SpinStarted") : -1;
        spinDuration = nbt.getLong("SpinDuration");
        initialAngle = nbt.getFloat("InitialAngle");
        finalAngle = nbt.getFloat("FinalAngle");
        chargeStrength = nbt.getFloat("ChargeStrength");
        result = nbt.getString("Result");
        chargeOwner = nbt.containsUuid("ChargeOwner") ? nbt.getUuid("ChargeOwner") : null;
    }
    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound n = new NbtCompound();
        writeTiming(n);
        return n;
    }
    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
