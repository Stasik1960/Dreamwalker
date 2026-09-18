package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record RepelModeTogglePayload() implements CustomPayload {
   public static final Id<RepelModeTogglePayload> TYPE = new Id<>(new Identifier("dannys-aot", "repel_mode_toggle"));
   public static final PacketCodec<PacketByteBuf, RepelModeTogglePayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, payload) -> {}, buf -> new RepelModeTogglePayload()
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
