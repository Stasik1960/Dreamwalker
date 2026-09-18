package daot.compat.network;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class PayloadTypeRegistry {
    private static final PayloadTypeRegistry C2S = new PayloadTypeRegistry();
    private static final PayloadTypeRegistry S2C = new PayloadTypeRegistry();
    private final Map<Identifier, PacketCodec<PacketByteBuf, ?>> codecs = new HashMap<>();

    public static PayloadTypeRegistry playC2S() { return C2S; }
    public static PayloadTypeRegistry playS2C() { return S2C; }
    public <T extends CustomPayload> void register(CustomPayload.Id<T> id, PacketCodec<PacketByteBuf, T> codec) {
        if (codecs.putIfAbsent(id.id(), codec) != null) {
            throw new IllegalStateException("Duplicate payload " + id.id());
        }
    }
    @SuppressWarnings("unchecked")
    <T extends CustomPayload> PacketCodec<PacketByteBuf, T> codec(CustomPayload.Id<T> id) {
        PacketCodec<PacketByteBuf, ?> value = codecs.get(id.id());
        if (value == null) throw new IllegalStateException("Unregistered payload " + id.id());
        return (PacketCodec<PacketByteBuf, T>) value;
    }
    @SuppressWarnings("unchecked")
    void encode(PacketByteBuf buffer, CustomPayload payload) {
        ((PacketCodec<PacketByteBuf, CustomPayload>) codec(payload.getId())).encode(buffer, payload);
    }
}
