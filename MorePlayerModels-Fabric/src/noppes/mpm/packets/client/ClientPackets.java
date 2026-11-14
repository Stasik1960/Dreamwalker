// Recovered from DreamWalker More Player Models 1.0.6-dw.3 and modified for dw.4.
// Noppes, atleast4IQ, DreamWalker. CC BY-NC 3.0. See LICENSE.txt.
package noppes.mpm.packets.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.class_1657;
import net.minecraft.class_2394;
import net.minecraft.class_2398;
import net.minecraft.class_2487;
import net.minecraft.class_2540;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_746;
import noppes.mpm.LogWriter;
import noppes.mpm.ModelData;
import noppes.mpm.ModelEyeData;
import noppes.mpm.MorePlayerModels;
import noppes.mpm.client.ChatMessages;
import noppes.mpm.client.ClientSkinUrlAccess;
import noppes.mpm.client.gui.GuiCreationScreenInterface;
import noppes.mpm.client.parts.MpmPartData;
import noppes.mpm.constants.EnumAnimation;
import noppes.mpm.packets.MpmPacket;
import noppes.mpm.packets.Packets;
import noppes.mpm.packets.server.PacketPing;
import noppes.mpm.packets.server.PacketPlayerDataUpdate;
import noppes.mpm.util.LatestIntervalSender;

public final class ClientPackets {
    private static final int MAX_PACKET_BYTES = 262144;
    private static final Map<UUID, PacketPlayerDataSend> pending = new LinkedHashMap<UUID, PacketPlayerDataSend>();
    private static boolean pinged;
    private static long nextRevision;
    private static long awaitingRevision;
    private static ClientProfileTransport.Result blockedUpload;
    private static boolean retryProfileHandshake;
    private static final LatestIntervalSender<PacketPlayerDataUpdate> outgoingProfiles;

    private ClientPackets() {
    }

    public static void register() {
        Packets.setClientSender(packet -> {
            if (packet instanceof PacketPlayerDataUpdate) {
                PacketPlayerDataUpdate update = (PacketPlayerDataUpdate)packet;
                class_2487 tag = update.data().method_10553();
                class_746 current = class_310.method_1551().field_1724;
                if (current != null) {
                    ClientSkinUrlAccess.sanitizeLocal(ModelData.get((class_1657)current), false, true);
                }
                ClientSkinUrlAccess.sanitizeProfile(tag, false);
                long base = Math.max(nextRevision, Math.max(0L, tag.method_10537("MpmSyncRevision")));
                awaitingRevision = nextRevision = base == Long.MAX_VALUE ? 1L : base + 1L;
                tag.method_10544("MpmSyncRevision", nextRevision);
                class_746 local = class_310.method_1551().field_1724;
                if (local != null) {
                    ModelData data = ModelData.get((class_1657)local);
                    data.setSyncRevision(nextRevision);
                    data.save();
                }
                outgoingProfiles.offer(new PacketPlayerDataUpdate(tag), System.nanoTime(), ClientPackets::sendNow);
            } else {
                ClientPackets.sendNow(packet);
            }
        });
        ClientPackets.receive(PacketSkinUrlPermission.ID, PacketSkinUrlPermission::read, packet -> ClientSkinUrlAccess.receivePermission(packet.allowed(), packet.rejected()));
        ClientPackets.receive(PacketPong.ID, PacketPong::read, packet -> {
            boolean bl = MorePlayerModels.HasServerSide = packet.version() == MorePlayerModels.Version;
            GuiCreationScreenInterface.Message = packet.version() == MorePlayerModels.Version ? "" : (packet.version() < MorePlayerModels.Version ? "message.lowerversion" : "message.higherversion");
        });
        ClientPackets.receive(PacketPlayerDataSend.ID, PacketPlayerDataSend::read, packet -> {
            if (packet.compound() == null) {
                return;
            }
            class_1657 player = ClientPacketHelper.getPlayer(packet.playerId());
            if (player == null) {
                if (pending.size() >= 512) {
                    pending.remove(pending.keySet().iterator().next());
                }
                pending.put(packet.playerId(), (PacketPlayerDataSend)packet);
                return;
            }
            pending.remove(packet.playerId());
            ClientPackets.applyProfile(player, packet);
        });
        ClientPackets.receive(PacketBackItemUpdate.ID, PacketBackItemUpdate::read, packet -> {
            class_1657 player = ClientPacketHelper.getPlayer(packet.playerId());
            if (player != null) {
                ModelData.get((class_1657)player).backItem = packet.item().method_7972();
            }
        });
        ClientPackets.receive(PacketAnimationStart.ID, PacketAnimationStart::read, packet -> {
            class_1657 player = ClientPacketHelper.getPlayer(packet.playerId());
            if (player == null) {
                return;
            }
            ModelData data = ModelData.get(player);
            if (!data.isMpmEnabled()) {
                return;
            }
            data.setAnimation(packet.animation());
            data.animationStart = player.field_6012;
            if (packet.animation() == EnumAnimation.SLEEP) {
                data.sleepRotation = player.field_6283;
            }
        });
        ClientPackets.receive(PacketEyeBlink.ID, PacketEyeBlink::read, packet -> {
            class_1657 player = ClientPacketHelper.getPlayer(packet.playerId());
            if (player == null) {
                return;
            }
            for (MpmPartData part : ModelData.get((class_1657)player).mpmParts) {
                if (!(part instanceof ModelEyeData)) continue;
                ModelEyeData eye = (ModelEyeData)part;
                eye.blinkStart = System.currentTimeMillis();
            }
        });
        ClientPackets.receive(PacketChatEvent.ID, PacketChatEvent::read, packet -> {
            class_1657 player = ClientPacketHelper.getPlayer(packet.playerId());
            if (player != null) {
                ChatMessages.getChatMessages(player.method_5477().getString()).addMessage(packet.message());
            }
        });
        ClientPackets.receive(PacketParticleLove.ID, PacketParticleLove::read, packet -> {
            class_1657 player = ClientPacketHelper.getPlayer(packet.playerId());
            if (player != null) {
                ModelData.get((class_1657)player).inLove = 40;
            }
        });
        ClientPackets.receive(PacketParticleNote.ID, PacketParticleNote::read, packet -> {
            class_1657 player = ClientPacketHelper.getPlayer(packet.playerId());
            if (player != null) {
                player.method_37908().method_8406((class_2394)class_2398.field_11224, player.method_23317(), player.method_23318() + 2.0, player.method_23321(), (double)packet.note() / 24.0, 0.0, 0.0);
            }
        });
        ClientPackets.receive(PacketParticleAngry.ID, PacketParticleAngry::read, packet -> {
            class_1657 player = ClientPacketHelper.getPlayer(packet.playerId());
            if (player == null) {
                return;
            }
            for (int i = 0; i < 5; ++i) {
                player.method_37908().method_8406((class_2394)class_2398.field_11231, player.method_23317() + ((double)player.method_6051().method_43057() - 0.5) * (double)player.method_17681() * 2.0, player.method_23318() + 0.8 + (double)(player.method_6051().method_43057() * player.method_17682()) / 2.0 - (double)ModelData.get(player).getBodyY(), player.method_23321() + ((double)player.method_6051().method_43057() - 0.5) * (double)player.method_17681() * 2.0, player.method_6051().method_43059() * 0.02, player.method_6051().method_43059() * 0.02, player.method_6051().method_43059() * 0.02);
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientSkinUrlAccess.reset();
            pinged = false;
            nextRevision = 0L;
            awaitingRevision = 0L;
            blockedUpload = null;
            retryProfileHandshake = false;
            outgoingProfiles.clear();
            MorePlayerModels.HasServerSide = false;
            pending.clear();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientSkinUrlAccess.reset();
            pinged = false;
            nextRevision = 0L;
            awaitingRevision = 0L;
            blockedUpload = null;
            retryProfileHandshake = false;
            outgoingProfiles.clear();
            MorePlayerModels.HasServerSide = false;
            pending.clear();
            if (!ModelData.flushPendingSaves(1000L)) {
                LogWriter.warn("Timed out flushing MPM client profiles on disconnect");
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (!ModelData.flushPendingSaves(3000L)) {
                LogWriter.warn("Timed out flushing MPM client profiles on shutdown");
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!pinged && client.field_1724 != null && ClientPlayNetworking.canSend((class_2960)PacketPing.ID)) {
                pinged = true;
                ModelData data = ModelData.get((class_1657)client.field_1724);
                nextRevision = Math.max(nextRevision, data.getSyncRevision());
                ClientSkinUrlAccess.sanitizeLocal(data, false, true);
                class_2487 profile = data.writeToNBT();
                ClientSkinUrlAccess.sanitizeProfile(profile, false);
                Packets.sendServer(new PacketPing(MorePlayerModels.Version, profile));
            }
            outgoingProfiles.flush(System.nanoTime(), ClientPackets::sendNow);
            pending.entrySet().removeIf(entry -> {
                class_1657 player = ClientPacketHelper.getPlayer((UUID)entry.getKey());
                if (player == null) {
                    return false;
                }
                ClientPackets.applyProfile(player, (PacketPlayerDataSend)entry.getValue());
                return true;
            });
        });
    }

    private static void applyProfile(class_1657 player, PacketPlayerDataSend packet) {
        boolean local = player == class_310.method_1551().field_1724;
        long acknowledged = Math.max(0L, packet.compound().method_10537("MpmSyncRevision"));
        if (local && (blockedUpload != null || (awaitingRevision > 0L && acknowledged < awaitingRevision))) {
            return;
        }
        ModelData data = ModelData.get(player);
        class_2487 profile = packet.compound().method_10553();
        ClientSkinUrlAccess.acceptServerProfile(player, profile);
        try {
            data.readFromNBT(profile);
        }
        catch (RuntimeException ex) {
            return;
        }
        if (local) {
            if (awaitingRevision > 0L && acknowledged >= awaitingRevision) {
                awaitingRevision = 0L;
            }
            nextRevision = Math.max(nextRevision, acknowledged);
            data.lastEdited = System.currentTimeMillis();
            data.save();
        }
    }

    private static boolean sendNow(MpmPacket packet) {
        if (packet instanceof PacketPlayerDataUpdate) {
            PacketPlayerDataUpdate update = (PacketPlayerDataUpdate)packet;
            class_2487 profile = update.data().method_10553();
            ClientSkinUrlAccess.sanitizeProfile(profile, false);
            packet = new PacketPlayerDataUpdate(profile);
        }
        boolean profile = packet instanceof PacketPing || packet instanceof PacketPlayerDataUpdate;
        if (retryProfileHandshake && packet instanceof PacketPlayerDataUpdate update) {
            packet = new PacketPing(MorePlayerModels.Version, update.data());
        }
        ClientProfileTransport.Result result = ClientProfileTransport.send(packet);
        if (result == ClientProfileTransport.Result.UNAVAILABLE) {
            return false;
        }
        if (profile) {
            if (result == ClientProfileTransport.Result.SENT) {
                blockedUpload = null;
                retryProfileHandshake = false;
            } else {
                if (packet instanceof PacketPing) {
                    retryProfileHandshake = true;
                }
                if (blockedUpload != result) {
                    warnProfileUpload(result);
                }
                // Protect the saved local profile from a stale server copy. A later
                // successful upload can acknowledge it through the existing revision check.
                blockedUpload = result;
            }
        }
        // An oversized packet rejected locally must be consumed by LatestIntervalSender.
        return true;
    }

    private static void warnProfileUpload(ClientProfileTransport.Result result) {
        String message = result == ClientProfileTransport.Result.REQUIRES_UPDATED_SERVER
            ? "More Player Models: this profile needs DreamWalker MPM dw.4 or newer on the server. Your profile was kept locally."
            : "More Player Models: this profile exceeds the transfer limit. Your profile was kept locally.";
        LogWriter.warn(message);
        class_746 player = class_310.method_1551().field_1724;
        if (player != null) {
            player.method_7353(class_2561.method_43470(message), false);
        }
    }

    private static <T extends MpmPacket> void receive(class_2960 id, Function<class_2540, T> decoder, Consumer<T> consumer) {
        ClientPlayNetworking.registerGlobalReceiver((class_2960)id, (client, handler, buffer, response) -> {
            T value;
            if (buffer.readableBytes() > 262144) {
                return;
            }
            try {
                value = decoder.apply(buffer);
            }
            catch (RuntimeException ex) {
                return;
            }
            client.execute(() -> consumer.accept(value));
        });
    }

    static {
        outgoingProfiles = new LatestIntervalSender<>(250000000L);
    }
}
