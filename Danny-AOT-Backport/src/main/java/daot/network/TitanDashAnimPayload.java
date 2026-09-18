package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TitanDashAnimPayload(int entityId) implements CustomPayload {
   public static final Id<TitanDashAnimPayload> TYPE = new Id<>(new Identifier("dannys-aot", "titan_dash_anim"));
   public static final PacketCodec<PacketByteBuf, TitanDashAnimPayload> STREAM_CODEC = PacketCodec.ofStatic(
      TitanDashAnimPayload::encode, TitanDashAnimPayload::decode
   );

   private static void encode(PacketByteBuf buf, TitanDashAnimPayload payload) {
      buf.writeVarInt(payload.entityId);
   }

   private static TitanDashAnimPayload decode(PacketByteBuf buf) {
      return new TitanDashAnimPayload(buf.readVarInt());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
