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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DwMagicConnectNetworking {
    public static final Identifier OPEN_SCREEN = DwMagicConnectMod.id("open_screen");
    public static final Identifier SAVE_SETTINGS = DwMagicConnectMod.id("save_settings");
    public static final Identifier CLOSE_SCREEN = DwMagicConnectMod.id("close_screen");

    private static final long SESSION_LIFETIME_NANOS = Duration.ofMinutes(2).toNanos();
    private static final Map<UUID, EditSession> EDIT_SESSIONS = new ConcurrentHashMap<>();

    private DwMagicConnectNetworking() {
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(SAVE_SETTINGS, (server, player, handler, buf, responseSender) -> {
            UUID token = buf.readUuid();
            int handOrdinal = buf.readVarInt();
            boolean enabled = buf.readBoolean();
            int frequencyA = buf.readVarInt();
            int frequencyB = buf.readVarInt();

            server.execute(() -> saveSettings(player, token, handOrdinal, enabled, frequencyA, frequencyB));
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

        MagicConnectData.RadioState state = MagicConnectData.read(stack);
        UUID token = UUID.randomUUID();
        EDIT_SESSIONS.put(
                player.getUuid(),
                new EditSession(
                        token,
                        hand,
                        stack,
                        stack.copy(),
                        state.deviceId(),
                        System.nanoTime() + SESSION_LIFETIME_NANOS
                )
        );

        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();

        PacketByteBuf payload = PacketByteBufs.create();
        payload.writeUuid(token);
        payload.writeVarInt(hand.ordinal());
        payload.writeBoolean(state.configured());
        payload.writeBoolean(state.enabled());
        payload.writeVarInt(state.frequencyA());
        payload.writeVarInt(state.frequencyB());
        ServerPlayNetworking.send(player, OPEN_SCREEN, payload);
    }

    private static void saveSettings(
            ServerPlayerEntity player,
            UUID token,
            int handOrdinal,
            boolean enabled,
            int frequencyA,
            int frequencyB
    ) {
        EditSession session = EDIT_SESSIONS.get(player.getUuid());
        if (session == null || !session.token().equals(token)) {
            reject(player);
            return;
        }

        // A matching save packet consumes the edit grant even if its remaining data is invalid.
        EDIT_SESSIONS.remove(player.getUuid(), session);

        if (session.expired()
                || handOrdinal < 0
                || handOrdinal >= Hand.values().length
                || Hand.values()[handOrdinal] != session.hand()) {
            reject(player);
            return;
        }

        if (!MagicConnectData.isFrequencyValid(frequencyA)
                || !MagicConnectData.isFrequencyValid(frequencyB)) {
            reject(player);
            return;
        }

        ItemStack currentStack = player.getStackInHand(session.hand());
        if (currentStack != session.stackReference()
                || !ItemStack.areEqual(currentStack, session.stackSnapshot())
                || !MagicConnectData.isRadio(currentStack)
                || MagicConnectData.deviceId(currentStack)
                .filter(session.deviceId()::equals)
                .isEmpty()) {
            reject(player);
            return;
        }

        MagicConnectData.saveSettings(currentStack, enabled, frequencyA, frequencyB);
        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
        player.sendMessage(Text.translatable("message.dw_magic_connect.saved"), false);
    }

    private static void closeSession(ServerPlayerEntity player, UUID token) {
        EditSession session = EDIT_SESSIONS.get(player.getUuid());
        if (session != null && session.token().equals(token)) {
            EDIT_SESSIONS.remove(player.getUuid(), session);
        }
    }

    private static void reject(ServerPlayerEntity player) {
        player.sendMessage(Text.translatable("message.dw_magic_connect.invalid_session"), false);
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
