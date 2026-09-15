package com.coraxberg.dwmagicconnect.network;

import com.coraxberg.dwmagicconnect.DwMagicConnectMod;
import com.coraxberg.dwmagicconnect.item.MagicConnectData;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DwMagicConnectNetworking {
    // Versioned IDs prevent a pre-channel client from decoding the new payload.
    public static final Identifier OPEN_SCREEN = DwMagicConnectMod.id("open_screen_v2");
    public static final Identifier SAVE_SETTINGS = DwMagicConnectMod.id("save_settings_v2");
    public static final Identifier CLOSE_SCREEN = DwMagicConnectMod.id("close_screen_v2");

    private static final int MAX_PACKET_CHANNEL_LENGTH = 64;
    private static final long SESSION_LIFETIME_NANOS = Duration.ofMinutes(2).toNanos();
    private static final Map<UUID, EditSession> EDIT_SESSIONS = new ConcurrentHashMap<>();

    private DwMagicConnectNetworking() {
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(SAVE_SETTINGS, (server, player, handler, buf, responseSender) -> {
            UUID token = buf.readUuid();
            int handOrdinal = buf.readVarInt();
            boolean enabled = buf.readBoolean();
            int transmitIndex = buf.readVarInt();
            List<ChannelPayload> channels = new ArrayList<>(MagicConnectData.CHANNEL_COUNT);
            for (int index = 0; index < MagicConnectData.CHANNEL_COUNT; index++) {
                channels.add(new ChannelPayload(
                        buf.readString(MAX_PACKET_CHANNEL_LENGTH),
                        buf.readString(MAX_PACKET_CHANNEL_LENGTH)
                ));
            }
            server.execute(() -> saveSettings(player, token, handOrdinal, enabled, transmitIndex, channels));
        });

        ServerPlayNetworking.registerGlobalReceiver(CLOSE_SCREEN, (server, player, handler, buf, responseSender) -> {
            UUID token = buf.readUuid();
            server.execute(() -> closeSession(player, token));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                EDIT_SESSIONS.remove(handler.player.getUuid()));
    }

    public static void openScreen(ServerPlayerEntity player, Hand hand, ItemStack stack) {
        if (player == null || hand == null || stack == null || stack.isEmpty()) return;
        if (player.getStackInHand(hand) != stack || !MagicConnectData.isRadio(stack)) return;
        if (!ServerPlayNetworking.canSend(player, OPEN_SCREEN)) {
            player.sendMessage(Text.translatable("message.dw_magic_connect.update_required"), false);
            return;
        }

        MagicConnectData.RadioState state = MagicConnectData.read(stack);
        UUID token = UUID.randomUUID();
        EDIT_SESSIONS.put(player.getUuid(), new EditSession(
                token, hand, stack, stack.copy(), state.deviceId(),
                System.nanoTime() + SESSION_LIFETIME_NANOS
        ));

        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();

        PacketByteBuf payload = PacketByteBufs.create();
        payload.writeUuid(token);
        payload.writeVarInt(hand.ordinal());
        payload.writeBoolean(state.enabled());
        payload.writeVarInt(state.transmitIndex());
        for (MagicConnectData.Frequency frequency : state.channels()) {
            payload.writeString(frequency.a());
            payload.writeString(frequency.b());
        }
        ServerPlayNetworking.send(player, OPEN_SCREEN, payload);
    }

    private static void saveSettings(
            ServerPlayerEntity player,
            UUID token,
            int handOrdinal,
            boolean enabled,
            int transmitIndex,
            List<ChannelPayload> channelPayloads
    ) {
        EditSession session = EDIT_SESSIONS.get(player.getUuid());
        if (session == null || !session.token().equals(token)) {
            reject(player);
            return;
        }

        // A matching save packet consumes the edit grant even if its data is invalid.
        EDIT_SESSIONS.remove(player.getUuid(), session);

        if (session.expired()
                || handOrdinal < 0
                || handOrdinal >= Hand.values().length
                || Hand.values()[handOrdinal] != session.hand()) {
            reject(player);
            return;
        }
        if (transmitIndex < 0 || transmitIndex >= MagicConnectData.CHANNEL_COUNT) {
            reject(player);
            return;
        }

        List<MagicConnectData.Frequency> channels = new ArrayList<>(MagicConnectData.CHANNEL_COUNT);
        for (ChannelPayload payload : channelPayloads) {
            if (!MagicConnectData.isFrequencyPairValid(payload.a(), payload.b())) {
                reject(player);
                return;
            }
            channels.add(new MagicConnectData.Frequency(payload.a(), payload.b()));
        }
        if (enabled && !channels.get(transmitIndex).configured()) {
            reject(player);
            return;
        }

        ItemStack currentStack = player.getStackInHand(session.hand());
        if (currentStack != session.stackReference()
                || !ItemStack.areEqual(currentStack, session.stackSnapshot())
                || !MagicConnectData.isRadio(currentStack)
                || MagicConnectData.deviceId(currentStack).filter(session.deviceId()::equals).isEmpty()) {
            reject(player);
            return;
        }

        try {
            MagicConnectData.saveSettings(currentStack, enabled, channels, transmitIndex);
        } catch (IllegalArgumentException exception) {
            reject(player);
            return;
        }
        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
        player.sendMessage(Text.translatable("message.dw_magic_connect.saved"), false);
    }

    private static void closeSession(ServerPlayerEntity player, UUID token) {
        EditSession session = EDIT_SESSIONS.get(player.getUuid());
        if (session != null && session.token().equals(token)) EDIT_SESSIONS.remove(player.getUuid(), session);
    }

    private static void reject(ServerPlayerEntity player) {
        player.sendMessage(Text.translatable("message.dw_magic_connect.invalid_session"), false);
    }

    private record ChannelPayload(String a, String b) {
    }

    private record EditSession(
            UUID token,
            Hand hand,
            ItemStack stackReference,
            ItemStack stackSnapshot,
            UUID deviceId,
            long expiresAtNanos
    ) {
        private boolean expired() {
            return System.nanoTime() > expiresAtNanos;
        }
    }
}
