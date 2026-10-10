package ru.modelprops.net;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import ru.modelprops.ModelPropItem;
import ru.modelprops.ModelProps;
import ru.modelprops.animation.AnimationSet;
import ru.modelprops.server.ServerModelEntry;
import ru.modelprops.server.ServerModelCatalog;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Server-authoritative start events for held-model animations and their sounds. */
public final class ModelActionNetworking {
    public static final Identifier ACTION_REQUEST = ModelProps.id("action_request");
    public static final Identifier ACTION_PLAY = ModelProps.id("action_play");
    public static final Identifier CLIP_REQUEST = ModelProps.id("clip_request");
    public static final Identifier CLIP_PLAY = ModelProps.id("clip_play");
    public static final int MAX_ACTION_LENGTH = 16;
    public static final int MAX_CLIP_LENGTH = 64;

    private static final Set<String> REQUESTABLE_ACTIONS = Set.of(
            "equip", "swing", "use", "attack", "custom"
    );
    private static final long MIN_REPEAT_TICKS = 2L;
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Map<CooldownKey, Long> LAST_ACTION_TICKS = new HashMap<>();

    private ModelActionNetworking() {
    }

    public static void initializeServer() {
        ServerPlayNetworking.registerGlobalReceiver(ACTION_REQUEST,
                (server, player, handler, buffer, responseSender) -> {
                    String action = buffer.readString(MAX_ACTION_LENGTH);
                    int handIndex = buffer.readByte();
                    Hand hand = handIndex == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND;
                    server.execute(() -> playHeldAction(player, hand, action));
                });
        ServerPlayNetworking.registerGlobalReceiver(CLIP_REQUEST,
                (server, player, handler, buffer, responseSender) -> {
                    String clipName = buffer.readString(MAX_CLIP_LENGTH);
                    int handIndex = buffer.readByte();
                    Hand hand = handIndex == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND;
                    server.execute(() -> playHeldClip(player, hand, clipName));
                });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LAST_ACTION_TICKS.keySet().removeIf(key -> key.playerId().equals(handler.player.getUuid())));
    }

    /** May be called by server-side item hooks as well as the validated request receiver. */
    public static boolean playHeldAction(ServerPlayerEntity player, Hand hand, String action) {
        if (!REQUESTABLE_ACTIONS.contains(action) || !AnimationSet.SUPPORTED_ACTIONS.contains(action)
                || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        ItemStack held = player.getStackInHand(hand);
        if (!(held.getItem() instanceof ModelPropItem)) {
            return false;
        }
        String modelId = ModelPropItem.getModelId(held);
        ServerModelEntry entry = ServerModelCatalog.INSTANCE.get(modelId);
        if (entry == null || !entry.hasAction(action)) {
            return false;
        }
        if (!claimCooldown(player, hand)) {
            return false;
        }

        long sequence = SEQUENCE.incrementAndGet();
        LinkedHashSet<ServerPlayerEntity> recipients = new LinkedHashSet<>(PlayerLookup.tracking(player));
        recipients.add(player);
        for (ServerPlayerEntity recipient : recipients) {
            if (!ServerPlayNetworking.canSend(recipient, ACTION_PLAY)) {
                continue;
            }
            PacketByteBuf packet = PacketByteBufs.create();
            packet.writeVarInt(player.getId());
            packet.writeByte(hand == Hand.OFF_HAND ? 1 : 0);
            packet.writeString(modelId, 128);
            packet.writeString(action, MAX_ACTION_LENGTH);
            packet.writeLong(sequence);
            ServerPlayNetworking.send(recipient, ACTION_PLAY, packet);
        }
        return true;
    }

    /** Starts a canonical named clip. The server derives the model from the held stack and validates it. */
    public static boolean playHeldClip(ServerPlayerEntity player, Hand hand, String clipName) {
        if (clipName == null || clipName.isBlank() || clipName.length() > MAX_CLIP_LENGTH
                || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        ItemStack held = player.getStackInHand(hand);
        if (!(held.getItem() instanceof ModelPropItem)) {
            return false;
        }
        String modelId = ModelPropItem.getModelId(held);
        ServerModelEntry entry = ServerModelCatalog.INSTANCE.get(modelId);
        if (entry == null || !entry.hasClip(clipName)) {
            return false;
        }
        if (!claimCooldown(player, hand)) {
            return false;
        }

        long sequence = SEQUENCE.incrementAndGet();
        LinkedHashSet<ServerPlayerEntity> recipients = new LinkedHashSet<>(PlayerLookup.tracking(player));
        recipients.add(player);
        for (ServerPlayerEntity recipient : recipients) {
            if (!ServerPlayNetworking.canSend(recipient, CLIP_PLAY)) {
                continue;
            }
            PacketByteBuf packet = PacketByteBufs.create();
            packet.writeVarInt(player.getId());
            packet.writeByte(hand == Hand.OFF_HAND ? 1 : 0);
            packet.writeString(modelId, 128);
            packet.writeString(clipName, MAX_CLIP_LENGTH);
            packet.writeLong(sequence);
            ServerPlayNetworking.send(recipient, CLIP_PLAY, packet);
        }
        return true;
    }

    private static boolean claimCooldown(ServerPlayerEntity player, Hand hand) {
        long tick = player.getServerWorld().getTime();
        CooldownKey key = new CooldownKey(player.getUuid(), hand);
        Long previous = LAST_ACTION_TICKS.get(key);
        if (previous != null && tick >= previous && tick - previous < MIN_REPEAT_TICKS) {
            return false;
        }
        LAST_ACTION_TICKS.put(key, tick);
        return true;
    }

    private record CooldownKey(UUID playerId, Hand hand) {
    }
}
