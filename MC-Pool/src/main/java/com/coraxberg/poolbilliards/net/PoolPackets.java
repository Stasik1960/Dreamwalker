package com.coraxberg.poolbilliards.net;

import com.coraxberg.poolbilliards.PoolBilliardsMod;
import com.coraxberg.poolbilliards.block.BilliardsTableBlockEntity;
import com.coraxberg.poolbilliards.game.PoolGameState;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class PoolPackets {
    public static final Identifier OPEN_SCREEN = PoolBilliardsMod.id("open_screen");
    public static final Identifier STATE = PoolBilliardsMod.id("state");
    public static final Identifier JOIN = PoolBilliardsMod.id("join");
    public static final Identifier SHOT = PoolBilliardsMod.id("shot");
    public static final Identifier RESET = PoolBilliardsMod.id("reset");
    public static final Identifier CLOSE = PoolBilliardsMod.id("close");
    public static final Identifier BALL_STYLE = PoolBilliardsMod.id("ball_style");

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(BALL_STYLE, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            boolean monochrome = buf.readBoolean();
            server.execute(() -> {
                if (player.getWorld().getBlockEntity(pos) instanceof BilliardsTableBlockEntity be) {
                    be.setBallStyle(player, monochrome);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(JOIN, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (player.getWorld().getBlockEntity(pos) instanceof BilliardsTableBlockEntity be) {
                    be.join(player);
                    sendState(player, pos, be.getGame());
                    broadcastState(player.getServerWorld(), pos, be.getGame());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SHOT, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            double angle = buf.readDouble();
            double power = buf.readDouble();
            server.execute(() -> {
                if (player.getWorld().getBlockEntity(pos) instanceof BilliardsTableBlockEntity be) {
                    be.shoot(player, angle, power);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RESET, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (player.getWorld().getBlockEntity(pos) instanceof BilliardsTableBlockEntity be) {
                    be.voteReset(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CLOSE, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (player.getWorld().getBlockEntity(pos) instanceof BilliardsTableBlockEntity be) {
                    be.leave(player);
                }
            });
        });
    }

    public static void sendOpen(ServerPlayerEntity player, BlockPos pos) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ServerPlayNetworking.send(player, OPEN_SCREEN, buf);
    }

    public static void sendState(ServerPlayerEntity player, BlockPos pos, PoolGameState game) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        game.write(buf);
        ServerPlayNetworking.send(player, STATE, buf);
    }

    public static void broadcastState(ServerWorld world, BlockPos pos, PoolGameState game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 128 * 128) {
                sendState(player, pos, game);
            }
        }
    }
}
