package ru.modelprops.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import ru.modelprops.ModelPropItem;
import ru.modelprops.animation.AnimationClip;
import ru.modelprops.animation.AnimationPose;
import ru.modelprops.animation.AnimationRuntime;
import ru.modelprops.animation.AnimationSet;
import ru.modelprops.client.sound.DynamicSoundPlayer;
import ru.modelprops.net.ModelActionNetworking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Connects server action events to the item renderer and runtime sound player. */
public final class ClientAnimationController {
    private static final long MAX_RETAIN_NANOS = 65_000_000_000L;
    private static final long PENDING_TTL_NANOS = 3_000_000_000L;
    private static final int MAX_PENDING_ACTIONS = 256;
    private static final List<String> AUTOMATIC_ACTIONS = List.of(
            "idle", "equip", "swing", "use", "attack"
    );
    private static final IdentityHashMap<ItemStack, ActivePlayback> ACTIVE = new IdentityHashMap<>();
    private static final Map<PlaybackKey, Long> LAST_SEQUENCES = new HashMap<>();
    private static final ArrayList<PendingPlayback> PENDING = new ArrayList<>();

    private static ItemStack previousMain = ItemStack.EMPTY;
    private static ItemStack previousOff = ItemStack.EMPTY;
    private static String previousMainId = "";
    private static String previousOffId = "";
    private static ClientModelEntry previousMainEntry;
    private static ClientModelEntry previousOffEntry;
    private static int previousSwingTicks = -1;
    private static boolean clipReceiverRegistered;

    private ClientAnimationController() {
    }

    public static void initialize() {
        AnimationRuntime.setProvider(ClientAnimationController::pose);
        if (!clipReceiverRegistered) {
            clipReceiverRegistered = true;
            ClientPlayNetworking.registerGlobalReceiver(ModelActionNetworking.CLIP_PLAY,
                    (client, handler, buffer, responseSender) -> {
                        int entityId = buffer.readVarInt();
                        Hand hand = buffer.readByte() == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND;
                        String modelId = buffer.readString(128);
                        String clipName = buffer.readString(ModelActionNetworking.MAX_CLIP_LENGTH);
                        long sequence = buffer.readLong();
                        client.execute(() -> playClip(client, entityId, hand, modelId, clipName, sequence));
                    });
        }
    }

    public static void tick(MinecraftClient client) {
        long now = System.nanoTime();
        ACTIVE.values().removeIf(active -> now - active.startedNanos > MAX_RETAIN_NANOS);
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) {
            previousMain = ItemStack.EMPTY;
            previousOff = ItemStack.EMPTY;
            previousMainId = "";
            previousOffId = "";
            previousMainEntry = null;
            previousOffEntry = null;
            previousSwingTicks = -1;
            return;
        }

        retryPending(client, now);

        ItemStack main = client.player.getMainHandStack();
        ItemStack off = client.player.getOffHandStack();
        String mainId = ModelPropItem.getModelId(main);
        String offId = ModelPropItem.getModelId(off);
        ClientModelEntry mainEntry = ClientModelCatalog.INSTANCE.get(mainId);
        ClientModelEntry offEntry = ClientModelCatalog.INSTANCE.get(offId);
        if (main != previousMain || !mainId.equals(previousMainId) || mainEntry != previousMainEntry) {
            requestIfAvailable(main, Hand.MAIN_HAND, "equip");
            previousMain = main;
            previousMainId = mainId;
            previousMainEntry = mainEntry;
        }
        if (off != previousOff || !offId.equals(previousOffId) || offEntry != previousOffEntry) {
            requestIfAvailable(off, Hand.OFF_HAND, "equip");
            previousOff = off;
            previousOffId = offId;
            previousOffEntry = offEntry;
        }

        if (client.player.handSwinging) {
            int ticks = client.player.handSwingTicks;
            boolean newSwing = ticks == 1 || (previousSwingTicks >= 0 && ticks < previousSwingTicks);
            if (newSwing && !client.options.useKey.isPressed()) {
                Hand hand = client.player.preferredHand;
                ItemStack stack = client.player.getStackInHand(hand);
                String action = client.crosshairTarget instanceof EntityHitResult ? "attack" : "swing";
                requestIfAvailable(stack, hand, action);
            }
            previousSwingTicks = ticks;
        } else {
            previousSwingTicks = -1;
        }
    }

    public static void triggerCustom(MinecraftClient client) {
        handleAnimationKey(client);
    }

    /** Opens the named-animation picker, starts its sole entry, or falls back to legacy custom. */
    public static void handleAnimationKey(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        if (ClientPlayNetworking.canSend(ModelActionNetworking.CLIP_REQUEST)) {
            HeldAnimations main = heldAnimations(client.player.getMainHandStack(), Hand.MAIN_HAND);
            HeldAnimations off = heldAnimations(client.player.getOffHandStack(), Hand.OFF_HAND);
            HeldAnimations selected = main != null ? main : off;
            if (selected != null) {
                if (selected.clipNames.size() == 1) {
                    triggerClip(selected.hand, selected.clipNames.get(0));
                } else {
                    client.setScreen(new AnimationSelectionScreen(
                            client.currentScreen, selected.hand, selected.clipNames));
                }
                return;
            }
        }

        triggerLegacyCustom(client);
    }

    private static void triggerLegacyCustom(MinecraftClient client) {
        ItemStack main = client.player.getMainHandStack();
        if (hasAction(main, "custom")) {
            requestAction(Hand.MAIN_HAND, "custom");
            return;
        }
        ItemStack off = client.player.getOffHandStack();
        if (hasAction(off, "custom")) {
            requestAction(Hand.OFF_HAND, "custom");
        }
    }

    private static HeldAnimations heldAnimations(ItemStack stack, Hand hand) {
        if (!(stack.getItem() instanceof ModelPropItem)) {
            return null;
        }
        ClientModelEntry entry = ClientModelCatalog.INSTANCE.get(ModelPropItem.getModelId(stack));
        if (entry == null) {
            return null;
        }
        AnimationSet animations = entry.animation();
        Set<String> automaticClips = new HashSet<>();
        for (String action : AUTOMATIC_ACTIONS) {
            animations.clipNameForAction(action).ifPresent(automaticClips::add);
        }
        String customClip = animations.clipNameForAction("custom").orElse(null);
        LinkedHashSet<String> manual = new LinkedHashSet<>();
        if (customClip != null && animations.hasClip(customClip)) {
            manual.add(customClip);
        }
        animations.clips().keySet().stream()
                .filter(clip -> !automaticClips.contains(clip) || clip.equals(customClip))
                .sorted(Comparator.naturalOrder())
                .forEach(manual::add);
        return manual.isEmpty() ? null : new HeldAnimations(hand, List.copyOf(manual));
    }

    /** Sends a canonical clip name; the server independently validates the held model and clip. */
    public static boolean triggerClip(Hand hand, String clipName) {
        if (hand == null || clipName == null || clipName.isBlank()
                || clipName.length() > ModelActionNetworking.MAX_CLIP_LENGTH
                || !ClientPlayNetworking.canSend(ModelActionNetworking.CLIP_REQUEST)) {
            return false;
        }
        PacketByteBuf packet = PacketByteBufs.create();
        packet.writeString(clipName, ModelActionNetworking.MAX_CLIP_LENGTH);
        packet.writeByte(hand == Hand.OFF_HAND ? 1 : 0);
        ClientPlayNetworking.send(ModelActionNetworking.CLIP_REQUEST, packet);
        return true;
    }

    public static void play(MinecraftClient client, int entityId, Hand hand,
                            String modelId, String action, long sequence) {
        enqueue(client, entityId, hand, modelId, PlaybackKind.ACTION, action, sequence);
    }

    public static void playClip(MinecraftClient client, int entityId, Hand hand,
                                String modelId, String clipName, long sequence) {
        enqueue(client, entityId, hand, modelId, PlaybackKind.CLIP, clipName, sequence);
    }

    private static void enqueue(MinecraftClient client, int entityId, Hand hand, String modelId,
                                PlaybackKind kind, String selector, long sequence) {
        PlaybackKey playbackKey = new PlaybackKey(entityId, hand);
        Long previous = LAST_SEQUENCES.get(playbackKey);
        if (previous != null && sequence <= previous) {
            return;
        }
        for (PendingPlayback pending : PENDING) {
            if (pending.entityId == entityId && pending.hand == hand && sequence <= pending.sequence) {
                return;
            }
        }
        long now = System.nanoTime();
        PendingPlayback pending = new PendingPlayback(entityId, hand, modelId, kind, selector,
                sequence, now + PENDING_TTL_NANOS);
        boolean earlierPending = PENDING.stream()
                .anyMatch(item -> item.entityId == entityId && item.hand == hand);
        if (!earlierPending && tryApply(client, pending, now)) {
            LAST_SEQUENCES.put(playbackKey, sequence);
            return;
        }
        if (PENDING.size() >= MAX_PENDING_ACTIONS) {
            PendingPlayback dropped = PENDING.remove(0);
            LAST_SEQUENCES.merge(new PlaybackKey(dropped.entityId, dropped.hand),
                    dropped.sequence, Math::max);
        }
        PENDING.add(pending);
    }

    private static boolean tryApply(MinecraftClient client, PendingPlayback pending, long now) {
        if (client.world == null) {
            return false;
        }
        Entity entity = client.world.getEntityById(pending.entityId);
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        ItemStack stack = living.getStackInHand(pending.hand);
        if (!pending.modelId.equals(ModelPropItem.getModelId(stack))) {
            Hand other = pending.hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
            ItemStack fallback = living.getStackInHand(other);
            if (!pending.modelId.equals(ModelPropItem.getModelId(fallback))) {
                return false;
            }
            stack = fallback;
        }
        ClientModelEntry entry = ClientModelCatalog.INSTANCE.get(pending.modelId);
        if (entry == null) {
            return false;
        }
        boolean hasAnimation = pending.kind == PlaybackKind.ACTION
                ? entry.animation().resolveAction(pending.selector).isPresent()
                : entry.animation().resolveDirectClip(pending.selector).isPresent();
        if (hasAnimation) {
            ACTIVE.put(stack, new ActivePlayback(pending.modelId, pending.kind, pending.selector, now));
        }
        byte[] sound = pending.kind == PlaybackKind.ACTION
                ? entry.soundBytesForAction(pending.selector)
                : entry.soundBytesForClip(pending.selector);
        if (!hasAnimation && sound == null) {
            return false;
        }
        if (sound != null) {
            DynamicSoundPlayer.play(sound, entity, 1.0F, 1.0F);
        }
        return true;
    }

    private static void retryPending(MinecraftClient client, long now) {
        Set<PlaybackKey> blockedHands = new HashSet<>();
        Iterator<PendingPlayback> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingPlayback pending = iterator.next();
            PlaybackKey playbackKey = new PlaybackKey(pending.entityId, pending.hand);
            Long previous = LAST_SEQUENCES.get(playbackKey);
            if (previous != null && pending.sequence <= previous) {
                iterator.remove();
                continue;
            }
            if (now >= pending.expiresNanos) {
                LAST_SEQUENCES.merge(playbackKey, pending.sequence, Math::max);
                iterator.remove();
                continue;
            }
            if (blockedHands.contains(playbackKey)) {
                continue;
            }
            if (tryApply(client, pending, now)) {
                LAST_SEQUENCES.put(playbackKey, pending.sequence);
                iterator.remove();
            } else {
                blockedHands.add(playbackKey);
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
        LAST_SEQUENCES.clear();
        PENDING.clear();
        previousMain = ItemStack.EMPTY;
        previousOff = ItemStack.EMPTY;
        previousMainId = "";
        previousOffId = "";
        previousMainEntry = null;
        previousOffEntry = null;
        previousSwingTicks = -1;
    }

    private static AnimationPose pose(ItemStack stack, String modelId, long nowNanos) {
        ClientModelEntry entry = ClientModelCatalog.INSTANCE.get(modelId);
        if (entry == null) {
            return AnimationPose.EMPTY;
        }
        AnimationSet animations = entry.animation();
        AnimationPose idle = animations.hasAction("idle")
                ? animations.sampleAction("idle", nowNanos / 1_000_000L)
                : AnimationPose.EMPTY;
        ActivePlayback active = ACTIVE.get(stack);
        if (active == null || !active.modelId.equals(modelId)) {
            return idle;
        }
        AnimationClip clip = active.kind == PlaybackKind.ACTION
                ? animations.resolveAction(active.selector).orElse(null)
                : animations.resolveDirectClip(active.selector).orElse(null);
        if (clip == null) {
            ACTIVE.remove(stack);
            return idle;
        }
        long elapsedMillis = Math.max(0L, (nowNanos - active.startedNanos) / 1_000_000L);
        if (elapsedMillis > clip.durationMillis()) {
            ACTIVE.remove(stack);
            return idle;
        }
        AnimationPose activePose = active.kind == PlaybackKind.ACTION
                ? animations.sampleAction(active.selector, elapsedMillis)
                : animations.sampleClip(active.selector, elapsedMillis);
        return idle.combine(activePose);
    }

    private static void requestIfAvailable(ItemStack stack, Hand hand, String action) {
        if (hasAction(stack, action)) {
            requestAction(hand, action);
        }
    }

    private static boolean hasAction(ItemStack stack, String action) {
        if (!(stack.getItem() instanceof ModelPropItem)) {
            return false;
        }
        ClientModelEntry entry = ClientModelCatalog.INSTANCE.get(ModelPropItem.getModelId(stack));
        return entry != null && (entry.animation().hasAction(action) || entry.sounds().containsKey(action));
    }

    private static void requestAction(Hand hand, String action) {
        if (!ClientPlayNetworking.canSend(ModelActionNetworking.ACTION_REQUEST)) {
            return;
        }
        PacketByteBuf packet = PacketByteBufs.create();
        packet.writeString(action, ModelActionNetworking.MAX_ACTION_LENGTH);
        packet.writeByte(hand == Hand.OFF_HAND ? 1 : 0);
        ClientPlayNetworking.send(ModelActionNetworking.ACTION_REQUEST, packet);
    }

    private enum PlaybackKind {
        ACTION,
        CLIP
    }

    private record ActivePlayback(String modelId, PlaybackKind kind, String selector, long startedNanos) {
    }

    private record PendingPlayback(int entityId, Hand hand, String modelId, PlaybackKind kind, String selector,
                                   long sequence, long expiresNanos) {
    }

    private record PlaybackKey(int entityId, Hand hand) {
    }

    private record HeldAnimations(Hand hand, List<String> clipNames) {
    }
}
