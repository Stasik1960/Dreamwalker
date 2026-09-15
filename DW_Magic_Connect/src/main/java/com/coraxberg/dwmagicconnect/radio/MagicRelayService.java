package com.coraxberg.dwmagicconnect.radio;

import com.coraxberg.dwmagicconnect.config.DwMagicConnectConfig;
import com.coraxberg.dwmagicconnect.item.MagicConnectData;
import com.coraxberg.rpchat.RpChatMod;
import com.coraxberg.rpchat.api.RpChatEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MagicRelayService {
    private MagicRelayService() {
    }

    public static void relay(RpChatEvents.LocalIcMessage event) {
        ServerPlayerEntity sender = event.sender();
        String message = event.message() == null ? "" : event.message().strip();
        if (message.isEmpty()) return;

        MinecraftServer server = sender.getServer();
        if (server == null) return;

        Set<MagicConnectData.Frequency> transmittedFrequencies = transmittedHeldFrequencies(sender);
        if (transmittedFrequencies.isEmpty()) return;

        List<ServerPlayerEntity> onlinePlayers = server.getPlayerManager().getPlayerList();
        Set<UUID> finalListenerIds = new LinkedHashSet<>();
        double relayRadius = DwMagicConnectConfig.relayRadius();
        double relayRadiusSquared = relayRadius * relayRadius;

        for (ServerPlayerEntity receiver : onlinePlayers) {
            if (receiver.getUuid().equals(sender.getUuid())) continue;
            if (!receivesAny(receiver, transmittedFrequencies)) continue;

            for (ServerPlayerEntity listener : onlinePlayers) {
                if (listener.getUuid().equals(sender.getUuid())) continue;
                if (listener.getWorld() != receiver.getWorld()) continue;
                if (listener.getPos().squaredDistanceTo(receiver.getPos()) > relayRadiusSquared) continue;

                finalListenerIds.add(listener.getUuid());
            }
        }

        if (finalListenerIds.isEmpty()) return;

        MutableText relayedMessage = Text.translatable("message.dw_magic_connect.radio")
                .formatted(Formatting.LIGHT_PURPLE)
                .append(RpChatMod.displayNameText(sender))
                .append(Text.literal(": ").formatted(Formatting.GRAY))
                .append(Text.literal(message).formatted(Formatting.WHITE));

        for (UUID listenerId : finalListenerIds) {
            ServerPlayerEntity listener = server.getPlayerManager().getPlayer(listenerId);
            if (listener != null) {
                listener.sendMessage(relayedMessage, false);
            }
        }
    }

    private static boolean receivesAny(
            ServerPlayerEntity player,
            Set<MagicConnectData.Frequency> transmittedFrequencies
    ) {
        for (MagicConnectData.Frequency frequency : listeningHeldFrequencies(player)) {
            if (transmittedFrequencies.contains(frequency)) return true;
        }
        return false;
    }

    private static Set<MagicConnectData.Frequency> transmittedHeldFrequencies(ServerPlayerEntity player) {
        Set<MagicConnectData.Frequency> frequencies = new LinkedHashSet<>();
        MagicConnectData.transmitFrequency(player.getStackInHand(Hand.MAIN_HAND)).ifPresent(frequencies::add);
        MagicConnectData.transmitFrequency(player.getStackInHand(Hand.OFF_HAND)).ifPresent(frequencies::add);
        return frequencies;
    }

    private static Set<MagicConnectData.Frequency> listeningHeldFrequencies(ServerPlayerEntity player) {
        Set<MagicConnectData.Frequency> frequencies = new LinkedHashSet<>();
        frequencies.addAll(MagicConnectData.listeningFrequencies(player.getStackInHand(Hand.MAIN_HAND)));
        frequencies.addAll(MagicConnectData.listeningFrequencies(player.getStackInHand(Hand.OFF_HAND)));
        return frequencies;
    }
}
