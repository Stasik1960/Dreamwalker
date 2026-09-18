package daot.compat.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ServerPlayNetworking {
    public record Context(MinecraftServer server, ServerPlayerEntity player) {}
    @FunctionalInterface public interface PlayPayloadHandler<T extends CustomPayload> {
        void receive(T payload, Context context);
    }
    public static <T extends CustomPayload> boolean registerGlobalReceiver(CustomPayload.Id<T> id, PlayPayloadHandler<T> handler) {
        return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(id.id(),
                (server, player, connection, buffer, sender) -> {
                    T payload = PayloadTypeRegistry.playC2S().codec(id).decode(buffer);
                    server.execute(() -> handler.receive(payload, new Context(server, player)));
                });
    }
    public static void send(ServerPlayerEntity player, CustomPayload payload) {
        PacketByteBuf buffer = PacketByteBufs.create();
        PayloadTypeRegistry.playS2C().encode(buffer, payload);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload.getId().id(), buffer);
    }
    private ServerPlayNetworking() {}
}
