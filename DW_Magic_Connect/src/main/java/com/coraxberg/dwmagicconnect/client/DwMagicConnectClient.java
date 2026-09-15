package com.coraxberg.dwmagicconnect.client;

import com.coraxberg.dwmagicconnect.network.DwMagicConnectNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;

import java.util.UUID;

public final class DwMagicConnectClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                DwMagicConnectNetworking.OPEN_SCREEN,
                (client, handler, buffer, responseSender) -> {
                    UUID token = buffer.readUuid();
                    int handOrdinal = buffer.readVarInt();
                    boolean configured = buffer.readBoolean();
                    boolean enabled = buffer.readBoolean();
                    int frequencyA = buffer.readVarInt();
                    int frequencyB = buffer.readVarInt();

                    client.execute(() -> openScreen(
                            client,
                            token,
                            handOrdinal,
                            configured,
                            enabled,
                            frequencyA,
                            frequencyB
                    ));
                }
        );
    }

    private static void openScreen(MinecraftClient client, UUID token, int handOrdinal,
                                   boolean configured, boolean enabled,
                                   int frequencyA, int frequencyB) {
        if (client.player == null || handOrdinal < 0 || handOrdinal >= Hand.values().length) {
            return;
        }

        client.setScreen(new MagicConnectScreen(
                token,
                handOrdinal,
                configured,
                enabled,
                frequencyA,
                frequencyB
        ));
    }

    static void sendSettings(UUID token, int handOrdinal, boolean enabled,
                             int frequencyA, int frequencyB) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeUuid(token);
        buffer.writeVarInt(handOrdinal);
        buffer.writeBoolean(enabled);
        buffer.writeVarInt(frequencyA);
        buffer.writeVarInt(frequencyB);
        ClientPlayNetworking.send(DwMagicConnectNetworking.SAVE_SETTINGS, buffer);
    }

    static void sendClose(UUID token) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeUuid(token);
        ClientPlayNetworking.send(DwMagicConnectNetworking.CLOSE_SCREEN, buffer);
    }
}
