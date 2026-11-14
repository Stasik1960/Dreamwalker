// Recovered from DreamWalker More Player Models 1.0.6-dw.3 and modified for dw.4.
// Noppes, atleast4IQ, DreamWalker. CC BY-NC 3.0. See LICENSE.txt.
package noppes.mpm.packets;

import io.netty.buffer.Unpooled;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_2487;
import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.server.MinecraftServer;
import noppes.mpm.ModelData;
import noppes.mpm.MorePlayerModels;
import noppes.mpm.ServerSkinUrlAccess;
import noppes.mpm.packets.client.PacketBackItemUpdate;
import noppes.mpm.packets.client.PacketPlayerDataSend;
import noppes.mpm.packets.client.PacketPong;
import noppes.mpm.packets.server.PacketAnimationUpdate;
import noppes.mpm.packets.server.PacketPing;
import noppes.mpm.packets.server.PacketPlayerDataUpdate;
import noppes.mpm.util.LatestWorkQueue;
import noppes.mpm.util.MPMScheduler;
import noppes.mpm.util.PacketRateGate;
import noppes.mpm.util.SkinUrlRules;

public final class Packets {
    private static final int MAX_PACKET_BYTES = 262144;
    private static final int MAX_ACTIVE_TRANSFERS = 64;
    private static final long TRANSFER_TIMEOUT_NANOS = 10_000_000_000L;
    // Access is guarded by admissionLock, like the profile queue and disconnect state.
    private static final Map<class_3222, IncomingProfile> incomingProfiles = new IdentityHashMap<>();
    private static Consumer<MpmPacket> clientSender = packet -> {};
    private static MinecraftServer server;
    private static volatile boolean accepting;
    private static final Object admissionLock;
    private static final WeakHashMap<class_3222, Boolean> closedConnections;
    private static volatile LatestWorkQueue<UUID, PendingProfile> pendingProfiles;
    private static volatile LatestWorkQueue<UUID, PendingAnimation> pendingAnimations;
    private static final PacketRateGate<UUID> profileRate;
    private static final PacketRateGate<UUID> pingRate;
    private static final PacketRateGate<UUID> animationRate;

    private Packets() {
    }

    public static void setServer(MinecraftServer value) {
        server = value;
        if (value != null) {
            pendingProfiles = new LatestWorkQueue<UUID, PendingProfile>(arg_0 -> ((MinecraftServer)value).execute(arg_0), 4096, (id, pending) -> Packets.deliverProfile(pending, false));
            pendingAnimations = new LatestWorkQueue<UUID, PendingAnimation>(arg_0 -> ((MinecraftServer)value).execute(arg_0), 4096, (id, pending) -> {
                if (accepting && !pending.player().method_14239()) {
                    PacketAnimationUpdate.setAnimation((class_1657)pending.player(), pending.packet().animation());
                }
            });
            accepting = true;
        } else {
            Object object = admissionLock;
            synchronized (object) {
                accepting = false;
                pendingProfiles = null;
                pendingAnimations = null;
                closedConnections.clear();
                incomingProfiles.clear();
            }
            profileRate.clear();
            pingRate.clear();
            animationRate.clear();
        }
    }

    public static void stopAndDrainProfiles() {
        Object object = admissionLock;
        synchronized (object) {
            accepting = false;
            incomingProfiles.clear();
            LatestWorkQueue<UUID, PendingProfile> queue = pendingProfiles;
            if (queue != null) {
                queue.drainInline();
            }
            if (pendingAnimations != null) {
                pendingAnimations.drainInline();
            }
        }
    }

    public static void flushPlayer(class_3222 player) {
        PendingProfile pending;
        UUID id = player.method_5667();
        Object object = admissionLock;
        synchronized (object) {
            closedConnections.put(player, Boolean.TRUE);
            incomingProfiles.remove(player);
            LatestWorkQueue<UUID, PendingProfile> queue = pendingProfiles;
            PendingProfile pendingProfile = pending = queue == null ? null : queue.cancelAndTake(id);
            if (pendingAnimations != null) {
                pendingAnimations.cancelAndTake(id);
            }
        }
        if (pending != null && pending.player() == player) {
            Packets.deliverProfile(pending, true);
        }
        profileRate.forget(id);
        pingRate.forget(id);
        animationRate.forget(id);
    }

    public static void setClientSender(Consumer<MpmPacket> sender) {
        clientSender = sender;
    }

    private static boolean admit(class_3222 player, PacketRateGate<UUID> gate) {
        Object object = admissionLock;
        synchronized (object) {
            return accepting && !closedConnections.containsKey(player) && !player.method_14239() && gate.allow(player.method_5667());
        }
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PacketPing.ID, (server, player, handler, buf, response) -> {
            if (buf.readableBytes() <= MAX_PACKET_BYTES && admit(player, pingRate)) {
                receivePing(player, buf, response, false);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(PacketPlayerDataUpdate.ID, (server, player, handler, buf, response) -> {
            if (buf.readableBytes() <= MAX_PACKET_BYTES && admit(player, profileRate)) {
                receiveProfileUpdate(player, buf, false);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(ProfileChunks.CHANNEL, (server, player, handler, buf, response) ->
            receiveProfileChunk(player, buf, response));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            synchronized (admissionLock) {
                if (!incomingProfiles.isEmpty()) {
                    expireTransfers(System.nanoTime());
                }
            }
        });
        ServerPlayNetworking.registerGlobalReceiver((class_2960)PacketAnimationUpdate.ID, (server, player, handler, buf, response) -> {
            PacketAnimationUpdate packet;
            if (buf.readableBytes() > 5 || !Packets.admit(player, animationRate)) {
                return;
            }
            try {
                packet = PacketAnimationUpdate.read(buf);
            }
            catch (RuntimeException ex) {
                return;
            }
            Object object = admissionLock;
            synchronized (object) {
                if (accepting && !closedConnections.containsKey(player) && pendingAnimations != null) {
                    pendingAnimations.submit(player.method_5667(), new PendingAnimation(player, packet));
                }
            }
        });
    }

    private static void receivePing(class_3222 player, class_2540 buffer, PacketSender response, boolean reassembled) {
        PacketPing packet;
        try {
            packet = PacketPing.read(buffer);
        } catch (RuntimeException ex) {
            return;
        }
        if (packet.data() == null || (reassembled && buffer.isReadable())) {
            return;
        }
        if (packet.version() != MorePlayerModels.Version) {
            if (ServerPlayNetworking.canSend(player, PacketPong.ID)) {
                class_2540 reply = PacketByteBufs.create();
                new PacketPong(MorePlayerModels.Version).write(reply);
                response.sendPacket(PacketPong.ID, reply);
            }
            return;
        }
        enqueueProfile(player, packet.data(), true);
    }

    private static void receiveProfileUpdate(class_3222 player, class_2540 buffer, boolean reassembled) {
        PacketPlayerDataUpdate packet;
        try {
            packet = PacketPlayerDataUpdate.read(buffer);
        } catch (RuntimeException ex) {
            return;
        }
        if (packet.data() != null && (!reassembled || !buffer.isReadable())) {
            enqueueProfile(player, packet.data(), false);
        }
    }

    private static void receiveProfileChunk(class_3222 player, class_2540 buffer, PacketSender response) {
        if (buffer.readableBytes() < ProfileChunks.HEADER_BYTES) {
            return;
        }
        int kind = buffer.readUnsignedByte();
        int total = buffer.readInt();
        int offset = buffer.readInt();
        if ((kind != ProfileChunks.PING && kind != ProfileChunks.UPDATE)
                || total <= ProfileChunks.LEGACY_LIMIT || total > ProfileChunks.PROFILE_LIMIT
                || offset < 0 || offset >= total || offset % ProfileChunks.CHUNK_BYTES != 0
                || buffer.readableBytes() != Math.min(ProfileChunks.CHUNK_BYTES, total - offset)) {
            return;
        }

        IncomingProfile transfer;
        synchronized (admissionLock) {
            if (!accepting || closedConnections.containsKey(player) || player.method_14239()) {
                return;
            }
            long now = System.nanoTime();
            expireTransfers(now);
            transfer = incomingProfiles.get(player);
            if (offset == 0) {
                if (transfer != null || incomingProfiles.size() >= MAX_ACTIVE_TRANSFERS
                        || !admit(player, kind == ProfileChunks.PING ? pingRate : profileRate)) {
                    return;
                }
                transfer = new IncomingProfile(kind, total, now + TRANSFER_TIMEOUT_NANOS);
                incomingProfiles.put(player, transfer);
            }
            if (transfer == null || transfer.kind != kind || transfer.bytes.length != total || transfer.offset != offset) {
                return;
            }
            int length = buffer.readableBytes();
            buffer.readBytes(transfer.bytes, offset, length);
            transfer.offset += length;
            if (transfer.offset != total) {
                return;
            }
            incomingProfiles.remove(player);
        }

        class_2540 payload = new class_2540(Unpooled.wrappedBuffer(transfer.bytes));
        try {
            if (kind == ProfileChunks.PING) {
                receivePing(player, payload, response, true);
            } else {
                receiveProfileUpdate(player, payload, true);
            }
        } finally {
            payload.release();
        }
    }

    private static void expireTransfers(long now) {
        incomingProfiles.values().removeIf(transfer -> now - transfer.deadline >= 0);
    }

    private static final class IncomingProfile {
        private final int kind;
        private final byte[] bytes;
        private final long deadline;
        private int offset;

        private IncomingProfile(int kind, int total, long deadline) {
            this.kind = kind;
            this.bytes = new byte[total];
            this.deadline = deadline;
        }
    }

    private static void enqueueProfile(class_3222 player, class_2487 tag, boolean handshake) {
        Object object = admissionLock;
        synchronized (object) {
            LatestWorkQueue<UUID, PendingProfile> queue = pendingProfiles;
            if (queue != null && accepting && !closedConnections.containsKey(player) && !player.method_14239()) {
                queue.submitMerged(player.method_5667(), new PendingProfile(player, tag, handshake), (oldValue, newValue) -> new PendingProfile(newValue.player(), newValue.tag(), oldValue.handshake() || newValue.handshake()));
            }
        }
    }

    private static void deliverProfile(PendingProfile pending, boolean allowDisconnected) {
        class_3222 player = pending.player();
        Packets.applyProfile(player, pending.tag(), allowDisconnected);
        if (pending.handshake() && !player.method_14239()) {
            Packets.sendNearby((class_1297)player, new PacketBackItemUpdate(player.method_5667(), player.method_31548().method_5438(0).method_7972()));
            Packets.send(player, new PacketPong(MorePlayerModels.Version));
        }
    }

    private static void applyProfile(class_3222 player, class_2487 profile, boolean allowDisconnected) {
        if (!allowDisconnected && player.method_14239()) {
            return;
        }
        boolean allowed = ServerSkinUrlAccess.refresh(player);
        class_2487 accepted = profile.method_10553();
        boolean rejected = SkinUrlRules.sanitize(accepted, allowed);
        ModelData data = ModelData.get((class_1657)player);
        try {
            data.readFromNBT(accepted);
        }
        catch (RuntimeException ex) {
            return;
        }
        if (!player.method_37908().method_8450().method_8355(MorePlayerModels.ALLOW_ENTITY_MODELS)) {
            data.setEntity(null);
        }
        data.save();
        Packets.sendNearby((class_1297)player, new PacketPlayerDataSend(player.method_5667(), data.writeToNBT()));
        if (rejected) {
            ServerSkinUrlAccess.reject(player);
        }
    }

    public static void send(class_3222 player, MpmPacket msg) {
        if (player == null || player.method_14239() || !ServerPlayNetworking.canSend((class_3222)player, (class_2960)msg.id())) {
            return;
        }
        class_2540 buffer = PacketByteBufs.create();
        msg.write(buffer);
        ServerPlayNetworking.send((class_3222)player, (class_2960)msg.id(), (class_2540)buffer);
    }

    public static void sendDelayed(class_3222 player, MpmPacket msg, int delay) {
        MPMScheduler.runTack(() -> {
            MinecraftServer current = player.method_5682();
            if (current != null) {
                current.execute(() -> Packets.send(player, msg));
            }
        }, delay);
    }

    public static void sendNearby(class_1297 entity, MpmPacket msg) {
        if (entity.method_37908().field_9236) {
            return;
        }
        for (class_3222 player : PlayerLookup.tracking((class_1297)entity)) {
            Packets.send(player, msg);
        }
        if (entity instanceof class_3222) {
            class_3222 player = (class_3222)entity;
            Packets.send(player, msg);
        }
    }

    public static void sendAll(MpmPacket msg) {
        if (server != null) {
            for (class_3222 player : server.method_3760().method_14571()) {
                Packets.send(player, msg);
            }
        }
    }

    public static void sendServer(MpmPacket msg) {
        clientSender.accept(msg);
    }

    public static void sendHandshake(class_3222 player, MpmPacket msg) {
        Packets.send(player, msg);
    }

    static {
        admissionLock = new Object();
        closedConnections = new WeakHashMap<>();
        profileRate = new PacketRateGate<>(8, 8.0, 4096);
        pingRate = new PacketRateGate<>(2, 1.0, 4096);
        animationRate = new PacketRateGate<>(10, 10.0, 4096);
    }

    private record PendingProfile(class_3222 player, class_2487 tag, boolean handshake) {
    }

    private record PendingAnimation(class_3222 player, PacketAnimationUpdate packet) {
    }
}
