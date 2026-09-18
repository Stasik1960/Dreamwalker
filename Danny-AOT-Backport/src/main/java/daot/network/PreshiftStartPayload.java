package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.PacketCodecs;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record PreshiftStartPayload(boolean playColossalNuke, boolean tease) implements CustomPayload {
   public static final Id<PreshiftStartPayload> TYPE = new Id<>(new Identifier("dannys-aot", "preshift_start"));
   public static final PacketCodec<PacketByteBuf, PreshiftStartPayload> STREAM_CODEC = PacketCodec.tuple(
      PacketCodecs.BOOL, PreshiftStartPayload::playColossalNuke, PacketCodecs.BOOL, PreshiftStartPayload::tease, PreshiftStartPayload::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
