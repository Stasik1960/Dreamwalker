package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ReversalTogglePayload() implements CustomPayload {
   public static final Id<ReversalTogglePayload> TYPE = new Id<>(new Identifier("dannys-aot", "reversal_toggle"));
   public static final PacketCodec<PacketByteBuf, ReversalTogglePayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, payload) -> {}, buf -> new ReversalTogglePayload()
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
