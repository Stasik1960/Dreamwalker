package daot.compat.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;

@Environment(EnvType.CLIENT)
public final class ClientPlayNetworking {
    public record Context(MinecraftClient client) {
        public ClientPlayerEntity player() { return client.player; }
    }
    @FunctionalInterface public interface PlayPayloadHandler<T extends CustomPayload> {
        void receive(T payload, Context context);
    }
    public static <T extends CustomPayload> boolean registerGlobalReceiver(CustomPayload.Id<T> id, PlayPayloadHandler<T> handler) {
        return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(id.id(),
                (client, connection, buffer, sender) -> {
                    T payload = PayloadTypeRegistry.playS2C().codec(id).decode(buffer);
                    client.execute(() -> handler.receive(payload, new Context(client)));
                });
    }
    public static boolean canSend(CustomPayload.Id<?> id) { return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(id.id()); }
    public static void send(CustomPayload payload) {
        PacketByteBuf buffer = PacketByteBufs.create();
        PayloadTypeRegistry.playC2S().encode(buffer, payload);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload.getId().id(), buffer);
    }
    private ClientPlayNetworking() {}
}
