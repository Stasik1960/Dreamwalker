package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TitanArmPayload(boolean armed) implements CustomPayload {
   public static final Id<TitanArmPayload> TYPE = new Id<>(new Identifier("dannys-aot", "titan_arm"));
   public static final PacketCodec<PacketByteBuf, TitanArmPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, payload) -> buf.writeBoolean(payload.armed()), buf -> new TitanArmPayload(buf.readBoolean())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
