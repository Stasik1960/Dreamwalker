package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TitanRoarPayload() implements CustomPayload {
   public static final Id<TitanRoarPayload> TYPE = new Id<>(new Identifier("dannys-aot", "titan_roar"));
   public static final PacketCodec<PacketByteBuf, TitanRoarPayload> STREAM_CODEC = PacketCodec.unit(new TitanRoarPayload());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
