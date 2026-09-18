package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record BonePositionPayload(double handX, double handY, double handZ, double forearmX, double forearmY, double forearmZ) implements CustomPayload {
   public static final Id<BonePositionPayload> TYPE = new Id<>(new Identifier("dannys-aot", "bone_position"));
   public static final PacketCodec<PacketByteBuf, BonePositionPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, BonePositionPayload>() {
      public BonePositionPayload decode(PacketByteBuf buf) {
         return new BonePositionPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
      }

      public void encode(PacketByteBuf buf, BonePositionPayload payload) {
         buf.writeDouble(payload.handX);
         buf.writeDouble(payload.handY);
         buf.writeDouble(payload.handZ);
         buf.writeDouble(payload.forearmX);
         buf.writeDouble(payload.forearmY);
         buf.writeDouble(payload.forearmZ);
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
