package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TitanAttackPayload() implements CustomPayload {
   public static final Id<TitanAttackPayload> TYPE = new Id<>(new Identifier("dannys-aot", "titan_attack"));
   public static final PacketCodec<PacketByteBuf, TitanAttackPayload> STREAM_CODEC = PacketCodec.unit(new TitanAttackPayload());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
