package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TeaseShiftPayload() implements CustomPayload {
   public static final Id<TeaseShiftPayload> TYPE = new Id<>(new Identifier("dannys-aot", "tease_shift"));
   public static final PacketCodec<PacketByteBuf, TeaseShiftPayload> STREAM_CODEC = PacketCodec.unit(new TeaseShiftPayload());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
