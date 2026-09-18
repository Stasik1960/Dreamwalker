package daot.compat.network;

import net.minecraft.network.PacketByteBuf;

public final class PacketCodecs {
    public static final PacketCodec<PacketByteBuf, Boolean> BOOL = PacketCodec.ofStatic(PacketByteBuf::writeBoolean, PacketByteBuf::readBoolean);
    public static final PacketCodec<PacketByteBuf, String> STRING = PacketCodec.ofStatic(PacketByteBuf::writeString, PacketByteBuf::readString);
    public static final PacketCodec<PacketByteBuf, Integer> VAR_INT = PacketCodec.ofStatic(PacketByteBuf::writeVarInt, PacketByteBuf::readVarInt);
    public static final PacketCodec<PacketByteBuf, Double> DOUBLE = PacketCodec.ofStatic(PacketByteBuf::writeDouble, PacketByteBuf::readDouble);
    public static final PacketCodec<PacketByteBuf, Float> FLOAT = PacketCodec.ofStatic(PacketByteBuf::writeFloat, PacketByteBuf::readFloat);
    private PacketCodecs() {}
}
