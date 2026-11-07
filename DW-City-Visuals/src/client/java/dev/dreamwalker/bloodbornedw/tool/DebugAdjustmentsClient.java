package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.EditorMeshUpdates;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class DebugAdjustmentsClient {
    private DebugAdjustmentsClient() {}

    public static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DebugAdjustments.resetClient());
        ClientPlayNetworking.registerGlobalReceiver(DebugAdjustments.SYNC, (client, handler, packet, sender) -> {
            try {
                Identifier dimension = packet.readIdentifier();
                boolean reset = packet.readBoolean();
                int count = packet.readVarInt();
                if (count < 0 || count > DebugAdjustments.SYNC_BATCH) return;
                Map<Long, DebugAdjustments.Entry> entries = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) entries.put(packet.readLong(), DebugAdjustments.readEntry(packet));
                if (packet.isReadable()) return;
                client.execute(() -> {
                    if (client.getNetworkHandler() != handler) return;
                    if (reset) DebugAdjustments.resetClient(dimension);
                    entries.forEach((root, entry) -> {
                        var old = DebugAdjustments.installClient(dimension, root, entry);
                        if (DebugAdjustments.hasPose(entry.value()) || old != null && DebugAdjustments.hasPose(old.value())) {
                            rebuild(client, dimension, BlockPos.fromLong(root));
                        }
                    });
                });
            } catch (RuntimeException invalid) {
                dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Invalid Dreamwalker debug adjustment packet", invalid);
            }
        });
    }

    private static void rebuild(MinecraftClient client, Identifier dimension, BlockPos root) {
        if (client.world == null || !client.world.getRegistryKey().getValue().equals(dimension)
                || !client.world.getChunkManager().isChunkLoaded(root.getX() >> 4, root.getZ() >> 4)) return;
        if (client.world.getBlockEntity(root) instanceof CompositeBlockEntity composite) EditorMeshUpdates.changed(composite);
        client.worldRenderer.scheduleBlockRenders(root.getX() - 1, root.getY() - 1, root.getZ() - 1,
                root.getX() + 1, root.getY() + 1, root.getZ() + 1);
    }
}
