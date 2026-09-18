package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderNoclipPayload(boolean wantsNoclip) implements CustomPayload {
   public static final Id<HomelanderNoclipPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_noclip"));
   public static final PacketCodec<PacketByteBuf, HomelanderNoclipPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, p) -> buf.writeBoolean(p.wantsNoclip), buf -> new HomelanderNoclipPayload(buf.readBoolean())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
