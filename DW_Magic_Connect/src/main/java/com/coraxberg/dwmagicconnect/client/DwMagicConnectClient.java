package com.coraxberg.dwmagicconnect.client;

import com.coraxberg.dwmagicconnect.item.MagicConnectData.Frequency;
import com.coraxberg.dwmagicconnect.network.DwMagicConnectNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import java.util.UUID;

public final class DwMagicConnectClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(DwMagicConnectNetworking.OPEN_SCREEN,
                (client, handler, buffer, responseSender) -> {
                    UUID token = buffer.readUuid();
                    int hand = buffer.readVarInt();
                    boolean enabled = buffer.readBoolean();
                    try {
                        Frequency frequency = new Frequency(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt());
                        boolean speaker = buffer.readBoolean();
                        int radius = buffer.readVarInt();
                        if (hand < 0 || hand >= Hand.values().length || radius < 1 || radius > 10) return;
                        client.execute(() -> {
                            if (client.player != null) client.setScreen(new MagicConnectScreen(token, hand, enabled, frequency, speaker, radius));
                        });
                    } catch (IllegalArgumentException exception) { /* Ignore invalid server state. */ }
                });
    }
    static void sendSettings(UUID token, int hand, boolean enabled, Frequency frequency, boolean speaker, int radius) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null || !ClientPlayNetworking.canSend(DwMagicConnectNetworking.SAVE_SETTINGS)) return;
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeUuid(token); buffer.writeVarInt(hand); buffer.writeBoolean(enabled);
        for (int i = 0; i < 4; i++) buffer.writeInt(frequency.hand(i));
        buffer.writeBoolean(speaker); buffer.writeVarInt(radius);
        ClientPlayNetworking.send(DwMagicConnectNetworking.SAVE_SETTINGS, buffer);
    }
    static void sendClose(UUID token) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null || !ClientPlayNetworking.canSend(DwMagicConnectNetworking.CLOSE_SCREEN)) return;
        PacketByteBuf buffer = PacketByteBufs.create(); buffer.writeUuid(token);
        ClientPlayNetworking.send(DwMagicConnectNetworking.CLOSE_SCREEN, buffer);
    }
}
