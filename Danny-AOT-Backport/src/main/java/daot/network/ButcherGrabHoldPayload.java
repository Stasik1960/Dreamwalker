package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ButcherGrabHoldPayload(boolean active) implements CustomPayload {
   public static final Id<ButcherGrabHoldPayload> TYPE = new Id<>(new Identifier("dannys-aot", "butcher_grab_hold"));
   public static final PacketCodec<PacketByteBuf, ButcherGrabHoldPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, p) -> buf.writeBoolean(p.active), buf -> new ButcherGrabHoldPayload(buf.readBoolean())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
