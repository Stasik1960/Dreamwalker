package dev.dreamwalker.bloodbornedw.composite;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;

public final class StudioOverhangClient implements ClientModInitializer {
    private record Transfer(int[] records, int next) {}
    private final Map<Identifier, Transfer> incoming = new HashMap<>();

    @Override public void onInitializeClient() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            incoming.clear();
            StudioOverhangs.clearClient();
        });
        ClientPlayNetworking.registerGlobalReceiver(StudioOverhangs.CHANNEL, (client, handler, buffer, sender) -> {
            var dimension = buffer.readIdentifier();
            int length = buffer.readVarInt(), start = buffer.readVarInt();
            var rows = buffer.readIntArray(StudioOverhangs.PACKET_INTS);
            if (length < 0 || length > StudioOverhangs.MAX_INTS || length % StudioOverhangs.STRIDE != 0
                    || start < 0 || start > length || rows.length > length - start)
                throw new IllegalArgumentException("Invalid Studio overhang packet");
            client.execute(() -> {
                if (client.getNetworkHandler() != handler) return;
                if (start == 0) incoming.put(dimension, new Transfer(new int[length], 0));
                var transfer = incoming.get(dimension);
                if (transfer == null || transfer.records.length != length || transfer.next != start) {
                    incoming.remove(dimension);
                    return;
                }
                System.arraycopy(rows, 0, transfer.records, start, rows.length);
                int next = start + rows.length;
                if (next == length) {
                    StudioOverhangs.acceptClient(dimension, transfer.records);
                    incoming.remove(dimension);
                } else {
                    incoming.put(dimension, new Transfer(transfer.records, next));
                }
            });
        });
    }
}
