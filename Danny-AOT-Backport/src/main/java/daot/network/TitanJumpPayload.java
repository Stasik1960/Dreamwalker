package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TitanJumpPayload() implements CustomPayload {
   public static final Id<TitanJumpPayload> TYPE = new Id<>(new Identifier("dannys-aot", "titan_jump"));
   public static final PacketCodec<PacketByteBuf, TitanJumpPayload> STREAM_CODEC = PacketCodec.unit(new TitanJumpPayload());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
