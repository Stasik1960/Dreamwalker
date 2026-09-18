package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record DenialTogglePayload() implements CustomPayload {
   public static final Id<DenialTogglePayload> TYPE = new Id<>(new Identifier("dannys-aot", "denial_toggle"));
   public static final PacketCodec<PacketByteBuf, DenialTogglePayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, payload) -> {}, buf -> new DenialTogglePayload()
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
