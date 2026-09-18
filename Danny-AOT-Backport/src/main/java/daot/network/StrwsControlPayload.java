package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record StrwsControlPayload(BlockPos controller, boolean active) implements CustomPayload {
   public static final Id<StrwsControlPayload> TYPE = new Id<>(new Identifier("dannys-aot", "strws_control"));
   public static final PacketCodec<PacketByteBuf, StrwsControlPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, StrwsControlPayload>() {
      public StrwsControlPayload decode(PacketByteBuf buf) {
         return new StrwsControlPayload(buf.readBlockPos(), buf.readBoolean());
      }

      public void encode(PacketByteBuf buf, StrwsControlPayload payload) {
         buf.writeBlockPos(payload.controller());
         buf.writeBoolean(payload.active());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
