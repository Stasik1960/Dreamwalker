package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.PacketCodecs;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record PreshiftEffectPayload(int entityId, boolean playColossalNuke) implements CustomPayload {
   public static final Id<PreshiftEffectPayload> TYPE = new Id<>(new Identifier("dannys-aot", "preshift_effect"));
   public static final PacketCodec<PacketByteBuf, PreshiftEffectPayload> STREAM_CODEC = PacketCodec.tuple(
      PacketCodecs.VAR_INT, PreshiftEffectPayload::entityId, PacketCodecs.BOOL, PreshiftEffectPayload::playColossalNuke, PreshiftEffectPayload::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
