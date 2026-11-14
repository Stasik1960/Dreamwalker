package net.fabricmc.fabric.api.client.networking.v1;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import net.minecraft.class_2540;
import net.minecraft.class_2817;
import net.minecraft.class_2960;
import io.netty.buffer.Unpooled;

public final class ClientPlayNetworking {
    public record Sent(class_2960 channel, byte[] bytes) {}
    public static final Set<class_2960> CAN_SEND = new HashSet<>();
    public static final List<Sent> SENT = new ArrayList<>();

    public static boolean canSend(class_2960 channel) {
        return CAN_SEND.contains(channel);
    }

    public static void send(class_2960 channel, class_2540 payload) {
        if (!canSend(channel)) throw new AssertionError("tried to use unadvertised channel " + channel);
        class_2540 wire = new class_2540(Unpooled.buffer());
        class_2817 decoded = null;
        try {
            new class_2817(channel, payload).method_11052(wire);
            decoded = new class_2817(wire);
            class_2540 data = decoded.method_36170();
            byte[] copy = new byte[data.readableBytes()];
            data.readBytes(copy);
            SENT.add(new Sent(decoded.method_36169(), copy));
        } finally {
            if (decoded != null) decoded.method_36170().release();
            payload.release();
            wire.release();
        }
    }
}
