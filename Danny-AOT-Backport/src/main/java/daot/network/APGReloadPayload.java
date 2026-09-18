package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record APGReloadPayload() implements CustomPayload {
   public static final Id<APGReloadPayload> TYPE = new Id<>(new Identifier("dannys-aot", "apg_reload"));
   public static final PacketCodec<PacketByteBuf, APGReloadPayload> STREAM_CODEC = PacketCodec.unit(new APGReloadPayload());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
