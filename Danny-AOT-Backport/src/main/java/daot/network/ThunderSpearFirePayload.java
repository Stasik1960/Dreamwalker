package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ThunderSpearFirePayload(int hand, float xRot, float yRot) implements CustomPayload {
   public static final Id<ThunderSpearFirePayload> TYPE = new Id<>(new Identifier("dannys-aot", "thunder_spear_fire"));
   public static final PacketCodec<PacketByteBuf, ThunderSpearFirePayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, ThunderSpearFirePayload>() {
      public ThunderSpearFirePayload decode(PacketByteBuf buf) {
         return new ThunderSpearFirePayload(buf.readVarInt(), buf.readFloat(), buf.readFloat());
      }

      public void encode(PacketByteBuf buf, ThunderSpearFirePayload payload) {
         buf.writeVarInt(payload.hand());
         buf.writeFloat(payload.xRot());
         buf.writeFloat(payload.yRot());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
