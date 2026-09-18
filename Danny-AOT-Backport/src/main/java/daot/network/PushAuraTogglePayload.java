package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record PushAuraTogglePayload() implements CustomPayload {
   public static final Id<PushAuraTogglePayload> TYPE = new Id<>(new Identifier("dannys-aot", "push_aura_toggle"));
   public static final PacketCodec<PacketByteBuf, PushAuraTogglePayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, payload) -> {}, buf -> new PushAuraTogglePayload()
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
