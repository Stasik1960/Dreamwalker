package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record StealthTogglePayload() implements CustomPayload {
   public static final Id<StealthTogglePayload> TYPE = new Id<>(new Identifier("dannys-aot", "stealth_toggle"));
   public static final PacketCodec<PacketByteBuf, StealthTogglePayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, payload) -> {}, buf -> new StealthTogglePayload()
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
