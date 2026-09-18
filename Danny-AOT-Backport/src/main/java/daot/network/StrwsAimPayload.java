package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record StrwsAimPayload(BlockPos controller, float yaw, float pitch) implements CustomPayload {
   public static final Id<StrwsAimPayload> TYPE = new Id<>(new Identifier("dannys-aot", "strws_aim"));
   public static final PacketCodec<PacketByteBuf, StrwsAimPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, StrwsAimPayload>() {
      public StrwsAimPayload decode(PacketByteBuf buf) {
         return new StrwsAimPayload(buf.readBlockPos(), buf.readFloat(), buf.readFloat());
      }

      public void encode(PacketByteBuf buf, StrwsAimPayload payload) {
         buf.writeBlockPos(payload.controller());
         buf.writeFloat(payload.yaw());
         buf.writeFloat(payload.pitch());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
