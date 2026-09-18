package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ButcherGrabScrollPayload(int notches) implements CustomPayload {
   public static final Id<ButcherGrabScrollPayload> TYPE = new Id<>(new Identifier("dannys-aot", "butcher_grab_scroll"));
   public static final PacketCodec<PacketByteBuf, ButcherGrabScrollPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, p) -> buf.writeInt(p.notches), buf -> new ButcherGrabScrollPayload(buf.readInt())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
