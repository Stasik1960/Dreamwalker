package com.coraxberg.poolbilliards.block;

import com.coraxberg.poolbilliards.PoolBilliardsMod;
import com.coraxberg.poolbilliards.game.PoolGameState;
import com.coraxberg.poolbilliards.net.PoolPackets;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.UUID;

public class BilliardsTableBlockEntity extends BlockEntity {
    private final PoolGameState game = new PoolGameState();
    private final HashSet<UUID> openPlayers = new HashSet<>();
    private int syncCooldown = 0;

    public BilliardsTableBlockEntity(BlockPos pos, BlockState state) {
        super(PoolBilliardsMod.BILLIARDS_TABLE_ENTITY, pos, state);
    }

    public PoolGameState getGame() {
        return game;
    }

    public void join(ServerPlayerEntity player) {
        openPlayers.add(player.getUuid());
        game.join(player.getUuid(), player.getName().getString());
        syncWorld();
    }

    public void leave(ServerPlayerEntity player) {
        openPlayers.remove(player.getUuid());
        game.leaveUi(player.getUuid());
        syncWorld();
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            PoolPackets.broadcastState(serverWorld, pos, game);
        }
    }

    public void shoot(ServerPlayerEntity player, double angle, double power) {
        if (game.shoot(player.getUuid(), angle, power)) {
            PoolGameState.PhysicsSound strike = new PoolGameState.PhysicsSound(
                    PoolGameState.SoundType.BALL, game.getBall(0).x, game.getBall(0).y,
                    8.0 + Math.max(0.0, Math.min(1.0, power)) * 50.5);
            playPhysicsSound(player.getServerWorld(), strike, SoundEvents.BLOCK_WOOD_HIT);
            syncCooldown = 0;
            syncWorld();
            PoolPackets.broadcastState(player.getServerWorld(), pos, game);
        }
    }

    public void voteReset(ServerPlayerEntity player) {
        game.voteReset(player.getUuid(), openPlayers);
        syncWorld();
        PoolPackets.broadcastState(player.getServerWorld(), pos, game);
    }

    public static void tick(World world, BlockPos pos, BlockState state, BilliardsTableBlockEntity be) {
        if (world.isClient) return;
        if (world instanceof ServerWorld serverWorld) {
            boolean changedOpenList = be.openPlayers.removeIf(id -> serverWorld.getServer().getPlayerManager().getPlayer(id) == null);
            if (changedOpenList) {
                be.game.syncActivePlayers(be.openPlayers);
                be.syncWorld();
                PoolPackets.broadcastState(serverWorld, pos, be.game);
            }
        }
        if (be.game.tickPhysics()) {
            ServerWorld serverWorld = (ServerWorld) world;
            for (PoolGameState.PhysicsSound sound : be.game.drainPhysicsSounds()) {
                SoundEvent event = switch (sound.type()) {
                    case BALL -> SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON;
                    case CUSHION -> SoundEvents.BLOCK_WOOD_HIT;
                    case POCKET -> SoundEvents.BLOCK_WOOL_FALL;
                };
                be.playPhysicsSound(serverWorld, sound, event);
            }
            be.markDirty();
            // Пока шары летят, шлём состояние не каждый тик. Когда они остановились,
            // финальное состояние нужно отправить сразу, иначе очки/право следующего удара
            // обновляются только после повторного открытия UI.
            boolean settled = !be.game.areBallsMoving();
            be.syncCooldown--;
            if (settled || be.syncCooldown <= 0) {
                be.syncCooldown = settled ? 0 : 1;
                be.syncWorld();
                PoolPackets.broadcastState(serverWorld, pos, be.game);
            }
        }
    }

    private void playPhysicsSound(ServerWorld serverWorld, PoolGameState.PhysicsSound sound, SoundEvent event) {
        double localX = -2.2 + sound.x() / PoolGameState.TABLE_W * 4.4;
        double localZ = -1.2 + sound.y() / PoolGameState.TABLE_H * 2.4;
        Direction facing = getCachedState().get(BilliardsTableBlock.FACING);
        double dx;
        double dz;
        switch (facing) {
            case SOUTH -> { dx = -localX; dz = -localZ; }
            case EAST -> { dx = -localZ; dz = localX; }
            case WEST -> { dx = localZ; dz = -localX; }
            default -> { dx = localX; dz = localZ; }
        }
        float volume = sound.type() == PoolGameState.SoundType.POCKET
                ? 0.48f
                : (float) (0.16 + 0.55 * Math.sqrt(Math.min(1.0, sound.strength() / 45.0)));
        float pitch = switch (sound.type()) {
            case BALL -> 1.45f;
            case CUSHION -> 0.9f;
            case POCKET -> 0.75f;
        };
        serverWorld.playSound(null, pos.getX() + 0.5 + dx, pos.getY() + 0.75,
                pos.getZ() + 0.5 + dz, event, SoundCategory.BLOCKS, volume, pitch);
    }

    private void syncWorld() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.put("game", game.toNbt());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("game")) game.fromNbt(nbt.getCompound("game"));
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = new NbtCompound();
        super.writeNbt(nbt);
        nbt.put("game", game.toClientNbt());
        return nbt;
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
