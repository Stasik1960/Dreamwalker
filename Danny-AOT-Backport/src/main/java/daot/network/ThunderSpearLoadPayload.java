package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ThunderSpearLoadPayload() implements CustomPayload {
   public static final Id<ThunderSpearLoadPayload> TYPE = new Id<>(new Identifier("dannys-aot", "thunder_spear_load"));
   public static final PacketCodec<PacketByteBuf, ThunderSpearLoadPayload> STREAM_CODEC = PacketCodec.unit(new ThunderSpearLoadPayload());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
