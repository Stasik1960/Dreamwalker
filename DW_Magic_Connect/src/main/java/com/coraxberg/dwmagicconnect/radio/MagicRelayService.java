package com.coraxberg.dwmagicconnect.radio;

import com.coraxberg.dwmagicconnect.item.MagicConnectData;
import com.coraxberg.rpchat.RpChatMod;
import com.coraxberg.rpchat.api.RpChatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MagicRelayService {
    private static final Set<ItemFrameEntity> LOADED_ITEM_FRAMES = new HashSet<>();

    private MagicRelayService() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ItemFrameEntity frame) LOADED_ITEM_FRAMES.add(frame);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof ItemFrameEntity frame) LOADED_ITEM_FRAMES.remove(frame);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED_ITEM_FRAMES.clear());
    }

    public static void relay(RpChatEvents.LocalIcMessage event) {
        ServerPlayerEntity sender = event.sender();
        String message = event.message() == null ? "" : event.message().strip();
        if (message.isEmpty()) return;

        MinecraftServer server = sender.getServer();
        if (server == null) return;

        List<RadioDevice> devices = collectDevices(server);
        Set<MagicConnectData.Frequency> transmitted = transmittedFrequencies(sender, devices);
        if (transmitted.isEmpty()) return;

        Map<UUID, Delivery> deliveries = new LinkedHashMap<>();
        for (RadioDevice receiver : devices) {
            if (!receiver.state().enabled()
                    || !receiver.state().frequency().configured()
                    || !transmitted.contains(receiver.state().frequency())) continue;

            double radiusSquared = receiver.state().radius() * receiver.state().radius();
            for (ServerPlayerEntity listener : server.getPlayerManager().getPlayerList()) {
                if (listener.getUuid().equals(sender.getUuid())) continue;
                if (listener.getWorld() != receiver.world()) continue;
                if (listener.getPos().squaredDistanceTo(receiver.position()) > radiusSquared) continue;

                boolean holder = receiver.held()
                        && receiver.ownerId() != null
                        && receiver.ownerId().equals(listener.getUuid());
                Delivery candidate = new Delivery(holder ? receiver.state().frequency() : null);
                deliveries.merge(listener.getUuid(), candidate,
                        (current, next) -> new Delivery(preferHolderFrequency(
                                current.holderFrequency(), next.holderFrequency())));
            }
        }

        for (Map.Entry<UUID, Delivery> entry : deliveries.entrySet()) {
            ServerPlayerEntity listener = server.getPlayerManager().getPlayer(entry.getKey());
            if (listener != null) listener.sendMessage(formatMessage(sender, message, entry.getValue()), false);
        }
    }

    private static Set<MagicConnectData.Frequency> transmittedFrequencies(
            ServerPlayerEntity sender,
            List<RadioDevice> devices
    ) {
        Set<MagicConnectData.Frequency> frequencies = new LinkedHashSet<>();
        for (RadioDevice device : devices) {
            MagicConnectData.RadioState state = device.state();
            boolean sameWorld = sender.getWorld() == device.world();
            double distanceSquared = sameWorld ? sender.getPos().squaredDistanceTo(device.position()) : Double.POSITIVE_INFINITY;
            if (captures(state, device.held(), sender.getUuid().equals(device.ownerId()), sameWorld, distanceSquared)) {
                frequencies.add(state.frequency());
            }
        }
        return frequencies;
    }

    private static List<RadioDevice> collectDevices(MinecraftServer server) {
        List<RadioDevice> devices = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ItemStack mainHand = player.getMainHandStack();
            ItemStack offHand = player.getOffHandStack();
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                boolean held = stack == mainHand || stack == offHand;
                addDevice(devices, stack, player.getServerWorld(), player.getPos(), player.getUuid(), held, false);
            }
        }

        for (ItemFrameEntity frame : List.copyOf(LOADED_ITEM_FRAMES)) {
            if (frame.isRemoved() || !(frame.getWorld() instanceof ServerWorld world)) continue;
            addDevice(devices, frame.getHeldItemStack(), world, frame.getPos(), null, false, true);
        }
        return devices;
    }

    private static void addDevice(
            List<RadioDevice> devices,
            ItemStack stack,
            ServerWorld world,
            Vec3d position,
            UUID ownerId,
            boolean held,
            boolean stationary
    ) {
        if (!MagicConnectData.isRadio(stack)) return;
        MagicConnectData.RadioState state = MagicConnectData.read(stack);
        if (stationary && !state.speaker()) return;
        if (!state.speaker() && !held) return;
        devices.add(new RadioDevice(world, position, ownerId, held, state));
    }

    private static MutableText formatMessage(ServerPlayerEntity sender, String message, Delivery delivery) {
        MutableText result;
        if (delivery.holderFrequency() != null) {
            result = Text.literal(frequencyLabel(delivery.holderFrequency())).formatted(Formatting.LIGHT_PURPLE);
        } else {
            result = Text.literal("[Мистический голос] ").formatted(Formatting.LIGHT_PURPLE);
        }
        return result.append(RpChatMod.displayNameText(sender))
                .append(Text.literal(": ").formatted(Formatting.GRAY))
                .append(Text.literal(message).formatted(Formatting.WHITE));
    }

    static String frequencyLabel(MagicConnectData.Frequency frequency) {
        int moonHour = frequency.moonHour() == 0 ? 12 : frequency.moonHour();
        int sunHour = frequency.sunHour() == 0 ? 12 : frequency.sunHour();
        return String.format(Locale.ROOT, "[%02d:%02d - %02d:%02d] ",
                moonHour, frequency.moonMinute(), sunHour, frequency.sunMinute());
    }

    static boolean captures(
            MagicConnectData.RadioState state,
            boolean held,
            boolean ownedBySender,
            boolean sameWorld,
            double distanceSquared
    ) {
        if (!state.enabled() || !state.frequency().configured()) return false;
        if (!state.speaker()) return held && ownedBySender;
        return sameWorld && distanceSquared <= state.radius() * state.radius();
    }

    static MagicConnectData.Frequency preferHolderFrequency(
            MagicConnectData.Frequency current,
            MagicConnectData.Frequency next
    ) {
        return current != null ? current : next;
    }

    private record RadioDevice(
            ServerWorld world,
            Vec3d position,
            UUID ownerId,
            boolean held,
            MagicConnectData.RadioState state
    ) {
    }

    private record Delivery(MagicConnectData.Frequency holderFrequency) {
    }
}
