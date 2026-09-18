package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HoodTogglePayload() implements CustomPayload {
   public static final Id<HoodTogglePayload> TYPE = new Id<>(new Identifier("dannys-aot", "hood_toggle"));
   public static final PacketCodec<PacketByteBuf, HoodTogglePayload> STREAM_CODEC = PacketCodec.ofStatic((buf, payload) -> {}, buf -> new HoodTogglePayload());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
