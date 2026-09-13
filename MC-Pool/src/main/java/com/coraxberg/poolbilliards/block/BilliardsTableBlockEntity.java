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
import net.minecraft.util.math.BlockPos;
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
            be.markDirty();
            // Пока шары летят, шлём состояние не каждый тик. Когда они остановились,
            // финальное состояние нужно отправить сразу, иначе очки/право следующего удара
            // обновляются только после повторного открытия UI.
            boolean settled = !be.game.areBallsMoving();
            be.syncCooldown--;
            if (settled || be.syncCooldown <= 0) {
                be.syncCooldown = settled ? 0 : 1;
                be.syncWorld();
                PoolPackets.broadcastState((ServerWorld) world, pos, be.game);
            }
        }
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
