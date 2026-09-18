package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record FounderAnimPayload(int entityId) implements CustomPayload {
   public static final Id<FounderAnimPayload> TYPE = new Id<>(new Identifier("dannys-aot", "founder_anim"));
   public static final PacketCodec<PacketByteBuf, FounderAnimPayload> STREAM_CODEC = PacketCodec.ofStatic(
      FounderAnimPayload::encode, FounderAnimPayload::decode
   );

   private static void encode(PacketByteBuf buf, FounderAnimPayload payload) {
      buf.writeVarInt(payload.entityId);
   }

   private static FounderAnimPayload decode(PacketByteBuf buf) {
      return new FounderAnimPayload(buf.readVarInt());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
