package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TitanShiftPayload() implements CustomPayload {
   public static final Id<TitanShiftPayload> TYPE = new Id<>(new Identifier("dannys-aot", "titan_shift"));
   public static final PacketCodec<PacketByteBuf, TitanShiftPayload> STREAM_CODEC = PacketCodec.unit(new TitanShiftPayload());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
