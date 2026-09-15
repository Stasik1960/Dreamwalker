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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DwMagicConnectNetworking {
    public static final Identifier OPEN_SCREEN = DwMagicConnectMod.id("open_screen_v4");
    public static final Identifier SAVE_SETTINGS = DwMagicConnectMod.id("save_settings_v4");
    public static final Identifier CLOSE_SCREEN = DwMagicConnectMod.id("close_screen_v4");

    private static final Map<UUID, EditSession> EDIT_SESSIONS = new ConcurrentHashMap<>();

    private DwMagicConnectNetworking() {
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(SAVE_SETTINGS, (server, player, handler, buf, responseSender) -> {
            try {
                SettingsPayload payload = readPayload(buf);
                server.execute(() -> saveSettings(player, payload));
            } catch (RuntimeException exception) {
                server.execute(() -> reject(player));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(CLOSE_SCREEN, (server, player, handler, buf, responseSender) -> {
            UUID token = buf.readUuid();
            server.execute(() -> closeSession(player, token));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> EDIT_SESSIONS.remove(handler.player.getUuid()));
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
                token, hand, stack, state.deviceId()
        ));

        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();

        PacketByteBuf payload = PacketByteBufs.create();
        writePayload(payload, new SettingsPayload(
                token, hand.ordinal(), state.enabled(), state.frequency(), state.speaker(), state.radius()
        ));
        ServerPlayNetworking.send(player, OPEN_SCREEN, payload);
    }

    static SettingsPayload readPayload(PacketByteBuf buf) {
        UUID token = buf.readUuid();
        int handOrdinal = buf.readVarInt();
        boolean enabled = buf.readBoolean();
        MagicConnectData.Frequency frequency = new MagicConnectData.Frequency(
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()
        );
        boolean speaker = buf.readBoolean();
        int radius = buf.readVarInt();
        return new SettingsPayload(token, handOrdinal, enabled, frequency, speaker, radius);
    }

    static void writePayload(PacketByteBuf buf, SettingsPayload payload) {
        buf.writeUuid(payload.token());
        buf.writeVarInt(payload.handOrdinal());
        buf.writeBoolean(payload.enabled());
        for (int hand = 0; hand < 4; hand++) buf.writeInt(payload.frequency().hand(hand));
        buf.writeBoolean(payload.speaker());
        buf.writeVarInt(payload.radius());
    }

    private static void saveSettings(ServerPlayerEntity player, SettingsPayload payload) {
        EditSession session = EDIT_SESSIONS.get(player.getUuid());
        if (session == null || !session.token().equals(payload.token())) {
            reject(player);
            return;
        }

        if (payload.handOrdinal() < 0
                || payload.handOrdinal() >= Hand.values().length
                || Hand.values()[payload.handOrdinal()] != session.hand()
                || !MagicConnectData.validRadius(payload.radius())) {
            EDIT_SESSIONS.remove(player.getUuid(), session);
            reject(player);
            return;
        }

        ItemStack currentStack = player.getStackInHand(session.hand());
        if (currentStack != session.stackReference()
                || !MagicConnectData.isRadio(currentStack)
                || MagicConnectData.deviceId(currentStack).filter(session.deviceId()::equals).isEmpty()) {
            EDIT_SESSIONS.remove(player.getUuid(), session);
            reject(player);
            return;
        }

        try {
            MagicConnectData.saveSettings(
                    currentStack, payload.enabled(), payload.frequency(), payload.speaker(), payload.radius()
            );
        } catch (IllegalArgumentException exception) {
            reject(player);
            return;
        }

        // Autosave keeps the same grant alive until close, disconnect, or item replacement.
        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }

    private static void closeSession(ServerPlayerEntity player, UUID token) {
        EditSession session = EDIT_SESSIONS.get(player.getUuid());
        if (session != null && session.token().equals(token)) EDIT_SESSIONS.remove(player.getUuid(), session);
    }

    private static void reject(ServerPlayerEntity player) {
        player.sendMessage(Text.translatable("message.dw_magic_connect.invalid_session"), false);
    }

    record SettingsPayload(
            UUID token,
            int handOrdinal,
            boolean enabled,
            MagicConnectData.Frequency frequency,
            boolean speaker,
            int radius
    ) {
    }

    private record EditSession(
            UUID token,
            Hand hand,
            ItemStack stackReference,
            UUID deviceId
    ) {
    }
}
