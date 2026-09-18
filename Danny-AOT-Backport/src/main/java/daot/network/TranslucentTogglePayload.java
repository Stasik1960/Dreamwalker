package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TranslucentTogglePayload(boolean active) implements CustomPayload {
   public static final Id<TranslucentTogglePayload> TYPE = new Id<>(new Identifier("dannys-aot", "translucent_toggle"));
   public static final PacketCodec<PacketByteBuf, TranslucentTogglePayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, p) -> buf.writeBoolean(p.active), buf -> new TranslucentTogglePayload(buf.readBoolean())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
