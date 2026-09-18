package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ZekesGlowPayload(int[] entityIds) implements CustomPayload {
   public static final Id<ZekesGlowPayload> TYPE = new Id<>(new Identifier("dannys-aot", "zekes_glow"));
   public static final PacketCodec<PacketByteBuf, ZekesGlowPayload> STREAM_CODEC = PacketCodec.ofStatic(ZekesGlowPayload::encode, ZekesGlowPayload::decode);

   private static void encode(PacketByteBuf buf, ZekesGlowPayload payload) {
      buf.writeVarInt(payload.entityIds.length);

      for (int id : payload.entityIds) {
         buf.writeVarInt(id);
      }
   }

   private static ZekesGlowPayload decode(PacketByteBuf buf) {
      int length = buf.readVarInt();
      int[] ids = new int[length];

      for (int i = 0; i < length; i++) {
         ids[i] = buf.readVarInt();
      }

      return new ZekesGlowPayload(ids);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
