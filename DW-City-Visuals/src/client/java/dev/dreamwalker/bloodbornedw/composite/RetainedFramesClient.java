package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.chunk.ChunkStatus;

public final class RetainedFramesClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(RetainedFrames.UPDATE, (client, handler, buf, sender) -> {
            var dimension = buf.readIdentifier(); var pos = buf.readBlockPos(); int profile = buf.readUnsignedByte() - 1;
            if (profile < -1 || profile > 1 || buf.isReadable()) throw new IllegalArgumentException("Invalid retained frame update");
            client.execute(() -> {
                if (client.getNetworkHandler() != handler || client.world == null || !client.world.getRegistryKey().getValue().equals(dimension)
                    || pos.getY() < client.world.getBottomY() || pos.getY() >= client.world.getTopY()) return;
                var chunk = client.world.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
                if (chunk == null) return;
                RetainedFrames.applyRemote(chunk, pos, profile);
                client.worldRenderer.scheduleBlockRenders(pos.getX()-1, pos.getY()-1, pos.getZ()-1, pos.getX()+1, pos.getY()+1, pos.getZ()+1);
            });
        });
    }
}
