import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.class_2487;
import net.minecraft.class_2540;
import net.minecraft.class_2960;
import noppes.mpm.packets.MpmPacket;
import noppes.mpm.packets.server.PacketPing;
import noppes.mpm.packets.server.PacketPlayerDataUpdate;

final class ClientFramingQa {
    private static final int LEGACY_LIMIT = 32_767;
    private static final int PROFILE_LIMIT = 245_760;
    private static final int BLOCK_SIZE = 30_000;
    private static final int HEADER_SIZE = 9;
    private static final class_2960 CHUNKS = new class_2960("moreplayermodels", "profile_chunks_v1");
    private final Method send;

    ClientFramingQa(String senderClass, String method) throws ReflectiveOperationException {
        send = Class.forName(senderClass).getDeclaredMethod(method, MpmPacket.class);
        send.setAccessible(true);
    }

    public static void main(String[] args) throws Exception {
        ClientFramingQa qa = new ClientFramingQa(args[0], args[1]);
        for (boolean ping : new boolean[]{true, false}) {
            qa.verify(ping, 100, false, false, "UNAVAILABLE", false);
            qa.verify(ping, 100, true, false, "SENT", false);
            qa.verify(ping, LEGACY_LIMIT, true, true, "SENT", false);
            qa.verify(ping, LEGACY_LIMIT + 1, true, true, "SENT", true);
            qa.verify(ping, 65_536, true, true, "SENT", true);
            qa.verify(ping, PROFILE_LIMIT, true, true, "SENT", true);
            qa.verify(ping, PROFILE_LIMIT + 1, true, true, "TOO_LARGE", false);
            qa.verify(ping, LEGACY_LIMIT + 1, true, false, "REQUIRES_UPDATED_SERVER", false);
        }
    }

    void verify(boolean ping, int size, boolean supportsLegacy, boolean supportsChunks, String expectedOutcome, boolean expectedChunks) throws Exception {
        MpmPacket packet = sizedPacket(ping, size);
        byte[] original = encode(packet);
        check(original.length == size, "size expected " + size + ", actual " + original.length);
        ClientPlayNetworking.SENT.clear();
        ClientPlayNetworking.CAN_SEND.clear();
        if (supportsLegacy) ClientPlayNetworking.CAN_SEND.add(packet.id());
        if (supportsChunks) ClientPlayNetworking.CAN_SEND.add(CHUNKS);
        try {
            Object result = send.invoke(null, packet);
            check(result instanceof Enum<?> && ((Enum<?>) result).name().equals(expectedOutcome),
                "send outcome expected " + expectedOutcome + ", actual " + result);
        } catch (InvocationTargetException failure) {
            throw new AssertionError("actual client sender failed", failure.getCause());
        }
        var sent = ClientPlayNetworking.SENT;
        if (!expectedOutcome.equals("SENT")) {
            check(sent.isEmpty(), "packet unexpectedly sent");
        } else if (!expectedChunks) {
            check(sent.size() == 1, "legacy packet split");
            check(sent.getFirst().channel().equals(packet.id()), "legacy channel changed");
            check(Arrays.equals(sent.getFirst().bytes(), original), "legacy packet data changed");
        } else {
            check(sent.size() == (size + BLOCK_SIZE - 1) / BLOCK_SIZE, "wrong frame count");
            int at = 0;
            ByteArrayOutputStream reassembled = new ByteArrayOutputStream(size);
            for (var frame : sent) {
                check(frame.channel().equals(CHUNKS), "fragment went to wrong channel");
                int length = Math.min(BLOCK_SIZE, size - at);
                check(frame.bytes().length == HEADER_SIZE + length, "wrong frame length");
                ByteBuffer bytes = ByteBuffer.wrap(frame.bytes());
                check(bytes.get() == (ping ? 1 : 2), "frame kind changed");
                check(bytes.getInt() == size, "transfer size changed");
                check(bytes.getInt() == at, "chunk offset not contiguous");
                byte[] part = new byte[length];
                bytes.get(part);
                reassembled.writeBytes(part);
                at += length;
            }
            check(Arrays.equals(original, reassembled.toByteArray()), "serialized profile changed in transfer");
        }
        System.out.printf("%s %,d bytes, peer chunks=%b, outcome=%s, frames=%d passed%n",
            packet.id(), size, supportsChunks, expectedOutcome, sent.size());
    }

    static MpmPacket sizedPacket(boolean ping, int size) {
        class_2487 profile = new class_2487();
        class_2487 extra = new class_2487();
        profile.method_10566("ExtraData", extra);
        profile.method_10556("MpmEnabled", true);
        profile.method_10544("MpmSyncRevision", 777L);
        extra.method_10570("Example", new byte[0]);
        MpmPacket packet = ping ? new PacketPing(8, profile) : new PacketPlayerDataUpdate(profile);
        int envelope = encode(packet).length;
        byte[] payload = new byte[size - envelope];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte)(i % 251);
        }
        extra.method_10570("Example", payload);
        return packet;
    }

    static byte[] encode(MpmPacket packet) {
        class_2540 buf = new class_2540(Unpooled.buffer());
        try {
            packet.write(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }

    static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
}
