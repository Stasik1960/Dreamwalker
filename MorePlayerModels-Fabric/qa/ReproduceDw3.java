import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.minecraft.class_2487;
import net.minecraft.class_2540;
import net.minecraft.class_2817;
import noppes.mpm.packets.MpmPacket;
import noppes.mpm.packets.server.PacketPing;
import noppes.mpm.packets.server.PacketPlayerDataUpdate;

public final class ReproduceDw3 {
    public static void main(String[] args) {
        for (boolean ping : new boolean[] {false, true}) {
            test(ping, 32_767, true);
            test(ping, 32_768, false);
            test(ping, 65_536, false);
        }
    }

    static class_2487 sizedProfile(boolean ping, int size) {
        class_2487 profile = new class_2487();
        class_2487 extra = new class_2487();
        profile.method_10566("ExtraData", extra);
        profile.method_10556("MpmEnabled", true);
        profile.method_10544("MpmSyncRevision", 17L);
        extra.method_10570("Example", new byte[0]);
        int envelope = payloadSize(packet(ping, profile));
        byte[] data = new byte[size - envelope];
        Arrays.fill(data, (byte)1);
        extra.method_10570("Example", data);
        return profile;
    }

    static MpmPacket packet(boolean ping, class_2487 profile) {
        return ping ? new PacketPing(6, profile) : new PacketPlayerDataUpdate(profile);
    }

    static int payloadSize(MpmPacket packet) {
        class_2540 payload = new class_2540(Unpooled.buffer());
        try {
            packet.write(payload);
            return payload.readableBytes();
        } finally {
            payload.release();
        }
    }

    static void test(boolean ping, int size, boolean expected) {
        MpmPacket packet = packet(ping, sizedProfile(ping, size));
        class_2540 payload = new class_2540(Unpooled.buffer());
        class_2540 wire = new class_2540(Unpooled.buffer());
        try {
            packet.write(payload);
            int actual = payload.readableBytes();
            check(actual == size, "size expected " + size + ", actual " + actual);
            new class_2817(packet.id(), payload).method_11052(wire);
            class_2817 decoded = null;
            try {
                decoded = new class_2817(wire);
                check(expected, "oversized packet decoded");
                check(decoded.method_36169().equals(packet.id()), "channel changed");
                check(decoded.method_36170().readableBytes() == size, "payload length changed");
                System.out.println(packet.id() + " " + actual + " accepted");
            } catch (IllegalArgumentException e) {
                check(!expected, "unexpected rejection " + e);
                check("Payload may not be larger than 32767 bytes".equals(e.getMessage()), "unexpected failure " + e);
                System.out.println(packet.id() + " " + actual + " rejected: " + e.getMessage());
            } finally {
                if (decoded != null) decoded.method_36170().release();
            }
        } finally {
            wire.release();
            payload.release();
        }
    }

    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
