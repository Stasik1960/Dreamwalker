package noppes.mpm.packets.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.class_2540;
import noppes.mpm.packets.MpmPacket;
import noppes.mpm.packets.ProfileChunks;
import noppes.mpm.packets.server.PacketPing;
import noppes.mpm.packets.server.PacketPlayerDataUpdate;

final class ClientProfileTransport {
    enum Result {
        SENT, UNAVAILABLE, TOO_LARGE, REQUIRES_UPDATED_SERVER
    }

    private ClientProfileTransport() {
    }

    static Result send(MpmPacket packet) {
        if (!ClientPlayNetworking.canSend(packet.id())) {
            return Result.UNAVAILABLE;
        }

        class_2540 payload = PacketByteBufs.create();
        boolean sentDirectly = false;
        try {
            packet.write(payload);
            int total = payload.readableBytes();
            if (total <= ProfileChunks.LEGACY_LIMIT) {
                ClientPlayNetworking.send(packet.id(), payload);
                sentDirectly = true;
                return Result.SENT;
            }

            int kind;
            if (packet instanceof PacketPing) {
                kind = ProfileChunks.PING;
            } else if (packet instanceof PacketPlayerDataUpdate) {
                kind = ProfileChunks.UPDATE;
            } else {
                return Result.TOO_LARGE;
            }
            if (total > ProfileChunks.PROFILE_LIMIT) {
                return Result.TOO_LARGE;
            }
            if (!ClientPlayNetworking.canSend(ProfileChunks.CHANNEL)) {
                return Result.REQUIRES_UPDATED_SERVER;
            }

            int start = payload.readerIndex();
            for (int offset = 0; offset < total; offset += ProfileChunks.CHUNK_BYTES) {
                int length = Math.min(ProfileChunks.CHUNK_BYTES, total - offset);
                class_2540 chunk = PacketByteBufs.create();
                boolean sent = false;
                try {
                    chunk.writeByte(kind);
                    chunk.writeInt(total);
                    chunk.writeInt(offset);
                    chunk.writeBytes(payload, start + offset, length);
                    ClientPlayNetworking.send(ProfileChunks.CHANNEL, chunk);
                    sent = true;
                } finally {
                    if (!sent) {
                        chunk.release();
                    }
                }
            }
            return Result.SENT;
        } finally {
            if (!sentDirectly) {
                payload.release();
            }
        }
    }
}
