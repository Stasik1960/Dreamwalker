package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.PacketCodecs;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record PlayBitePayload(int entityId) implements CustomPayload {
   public static final Id<PlayBitePayload> TYPE = new Id<>(new Identifier("dannys-aot", "play_bite"));
   public static final PacketCodec<PacketByteBuf, PlayBitePayload> STREAM_CODEC = PacketCodec.tuple(
      PacketCodecs.VAR_INT, PlayBitePayload::entityId, PlayBitePayload::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
