package com.coraxberg.dwmagicconnect.client;

import com.coraxberg.dwmagicconnect.item.MagicConnectData;
import com.coraxberg.dwmagicconnect.network.DwMagicConnectNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DwMagicConnectClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                DwMagicConnectNetworking.OPEN_SCREEN,
                (client, handler, buffer, responseSender) -> {
                    UUID token = buffer.readUuid();
                    int handOrdinal = buffer.readVarInt();
                    boolean enabled = buffer.readBoolean();
                    int transmitIndex = buffer.readVarInt();
                    List<MagicConnectData.Frequency> channels = new ArrayList<>(MagicConnectData.CHANNEL_COUNT);
                    try {
                        for (int index = 0; index < MagicConnectData.CHANNEL_COUNT; index++) {
                            channels.add(new MagicConnectData.Frequency(
                                    buffer.readString(MagicConnectData.MAX_CHANNEL_LENGTH),
                                    buffer.readString(MagicConnectData.MAX_CHANNEL_LENGTH)
                            ));
                        }
                    } catch (IllegalArgumentException exception) {
                        return;
                    }

                    client.execute(() -> openScreen(client, token, handOrdinal, enabled, channels, transmitIndex));
                }
        );
    }

    private static void openScreen(
            MinecraftClient client,
            UUID token,
            int handOrdinal,
            boolean enabled,
            List<MagicConnectData.Frequency> channels,
            int transmitIndex
    ) {
        if (client.player == null || handOrdinal < 0 || handOrdinal >= Hand.values().length) return;
        client.setScreen(new MagicConnectScreen(token, handOrdinal, enabled, channels, transmitIndex));
    }

    static void sendSettings(
            UUID token,
            int handOrdinal,
            boolean enabled,
            List<MagicConnectData.Frequency> channels,
            int transmitIndex
    ) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeUuid(token);
        buffer.writeVarInt(handOrdinal);
        buffer.writeBoolean(enabled);
        buffer.writeVarInt(transmitIndex);
        for (MagicConnectData.Frequency frequency : channels) {
            buffer.writeString(frequency.a());
            buffer.writeString(frequency.b());
        }
        ClientPlayNetworking.send(DwMagicConnectNetworking.SAVE_SETTINGS, buffer);
    }

    static void sendClose(UUID token) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeUuid(token);
        ClientPlayNetworking.send(DwMagicConnectNetworking.CLOSE_SCREEN, buffer);
    }
}
