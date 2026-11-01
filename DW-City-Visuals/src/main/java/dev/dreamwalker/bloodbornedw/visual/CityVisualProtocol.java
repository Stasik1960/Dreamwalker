package dev.dreamwalker.bloodbornedw.visual;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class CityVisualProtocol {
    public static final int PROTOCOL = 2;
    public static final Identifier LOGIN = new Identifier("bloodborne_dw", "city_visual_login");
    public static final Identifier MODE = new Identifier("bloodborne_dw", "city_visual_mode");
    public record Offer(boolean alt, long revision, String fingerprint) {
        public Offer {
            if (revision < 0 || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid visual offer");
        }
    }
    public record Response(boolean ready, Offer offer) {}
    private static void writeFields(PacketByteBuf buf, Offer offer) {
        buf.writeBoolean(offer.alt()); buf.writeLong(offer.revision()); buf.writeString(offer.fingerprint(), 64);
    }
    private static boolean readBoolean(PacketByteBuf buf) {
        byte value = buf.readByte();
        if (value != 0 && value != 1) throw new IllegalArgumentException("Invalid visual boolean");
        return value == 1;
    }
    private static Offer readFields(PacketByteBuf buf) {
        return new Offer(readBoolean(buf), buf.readLong(), buf.readString(64));
    }
    public static void writeOffer(PacketByteBuf buf, Offer offer) { buf.writeVarInt(PROTOCOL); writeFields(buf, offer); }
    public static Offer readOffer(PacketByteBuf buf) {
        if (buf.readableBytes() > 80 || buf.readVarInt() != PROTOCOL) throw new IllegalArgumentException("Invalid visual protocol");
        Offer offer = readFields(buf);
        if (buf.isReadable()) throw new IllegalArgumentException("Trailing visual data");
        return offer;
    }
    public static void writeResponse(PacketByteBuf buf, Response response) {
        buf.writeVarInt(PROTOCOL); buf.writeBoolean(response.ready()); writeFields(buf, response.offer());
    }
    public static Response readResponse(PacketByteBuf buf) {
        if (buf.readableBytes() > 81 || buf.readVarInt() != PROTOCOL) throw new IllegalArgumentException("Invalid visual protocol");
        boolean ready = readBoolean(buf); Offer offer = readFields(buf);
        if (buf.isReadable()) throw new IllegalArgumentException("Trailing visual data");
        return new Response(ready, offer);
    }
    private CityVisualProtocol() {}
}
