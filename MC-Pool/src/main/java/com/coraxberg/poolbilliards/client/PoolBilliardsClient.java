package com.coraxberg.poolbilliards.client;

import com.coraxberg.poolbilliards.PoolBilliardsMod;
import com.coraxberg.poolbilliards.client.render.BilliardsTableBlockEntityRenderer;
import com.coraxberg.poolbilliards.game.PoolGameState;
import com.coraxberg.poolbilliards.net.PoolPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

public class PoolBilliardsClient implements ClientModInitializer {
    private static PoolTableScreen currentScreen;

    @Override
    public void onInitializeClient() {
        BlockEntityRendererRegistry.register(PoolBilliardsMod.BILLIARDS_TABLE_ENTITY, BilliardsTableBlockEntityRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(PoolPackets.OPEN_SCREEN, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> openScreen(pos));
        });

        ClientPlayNetworking.registerGlobalReceiver(PoolPackets.STATE, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            PoolGameState state = PoolGameState.read(buf);
            client.execute(() -> {
                if (currentScreen != null && currentScreen.isForTable(pos)) {
                    currentScreen.setState(state);
                }
            });
        });
    }

    public static void openScreen(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        currentScreen = new PoolTableScreen(pos);
        client.setScreen(currentScreen);
        PacketByteBuf join = PacketByteBufs.create();
        join.writeBlockPos(pos);
        ClientPlayNetworking.send(PoolPackets.JOIN, join);
    }

    public static void sendShot(BlockPos pos, double angle, double power) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeDouble(angle);
        buf.writeDouble(power);
        ClientPlayNetworking.send(PoolPackets.SHOT, buf);
    }

    public static void sendReset(BlockPos pos) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(PoolPackets.RESET, buf);
    }

    public static void sendClose(BlockPos pos) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(PoolPackets.CLOSE, buf);
        if (currentScreen != null && currentScreen.isForTable(pos)) currentScreen = null;
    }
}
