package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.PacketCodecs;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record PushAuraStatePayload(int entityId, boolean active, boolean attract) implements CustomPayload {
   public static final Id<PushAuraStatePayload> TYPE = new Id<>(new Identifier("dannys-aot", "push_aura_state"));
   public static final PacketCodec<PacketByteBuf, PushAuraStatePayload> STREAM_CODEC = PacketCodec.tuple(
      PacketCodecs.VAR_INT,
      PushAuraStatePayload::entityId,
      PacketCodecs.BOOL,
      PushAuraStatePayload::active,
      PacketCodecs.BOOL,
      PushAuraStatePayload::attract,
      PushAuraStatePayload::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
