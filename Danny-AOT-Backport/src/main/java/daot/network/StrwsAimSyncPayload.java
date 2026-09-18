package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record StrwsAimSyncPayload(BlockPos controller, float yaw, float pitch) implements CustomPayload {
   public static final Id<StrwsAimSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "strws_aim_sync"));
   public static final PacketCodec<PacketByteBuf, StrwsAimSyncPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, StrwsAimSyncPayload>() {
      public StrwsAimSyncPayload decode(PacketByteBuf buf) {
         return new StrwsAimSyncPayload(buf.readBlockPos(), buf.readFloat(), buf.readFloat());
      }

      public void encode(PacketByteBuf buf, StrwsAimSyncPayload payload) {
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
