package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TargetGlowPayload(int[] entityIds) implements CustomPayload {
   public static final Id<TargetGlowPayload> TYPE = new Id<>(new Identifier("dannys-aot", "target_glow"));
   public static final PacketCodec<PacketByteBuf, TargetGlowPayload> STREAM_CODEC = PacketCodec.ofStatic(TargetGlowPayload::encode, TargetGlowPayload::decode);

   private static void encode(PacketByteBuf buf, TargetGlowPayload payload) {
      buf.writeVarInt(payload.entityIds.length);

      for (int id : payload.entityIds) {
         buf.writeVarInt(id);
      }
   }

   private static TargetGlowPayload decode(PacketByteBuf buf) {
      int length = buf.readVarInt();
      int[] ids = new int[length];

      for (int i = 0; i < length; i++) {
         ids[i] = buf.readVarInt();
      }

      return new TargetGlowPayload(ids);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
