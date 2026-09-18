package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record APGFirePayload(float xRot, float yRot, int handMask) implements CustomPayload {
   public static final Id<APGFirePayload> TYPE = new Id<>(new Identifier("dannys-aot", "apg_fire"));
   public static final PacketCodec<PacketByteBuf, APGFirePayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, APGFirePayload>() {
      public APGFirePayload decode(PacketByteBuf buf) {
         return new APGFirePayload(buf.readFloat(), buf.readFloat(), buf.readVarInt());
      }

      public void encode(PacketByteBuf buf, APGFirePayload payload) {
         buf.writeFloat(payload.xRot());
         buf.writeFloat(payload.yRot());
         buf.writeVarInt(payload.handMask());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
