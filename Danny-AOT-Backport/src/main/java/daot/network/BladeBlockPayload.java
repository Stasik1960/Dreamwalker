package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record BladeBlockPayload(boolean blocking) implements CustomPayload {
   public static final Id<BladeBlockPayload> TYPE = new Id<>(new Identifier("dannys-aot", "blade_block"));
   public static final PacketCodec<PacketByteBuf, BladeBlockPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, BladeBlockPayload>() {
      public BladeBlockPayload decode(PacketByteBuf buf) {
         return new BladeBlockPayload(buf.readBoolean());
      }

      public void encode(PacketByteBuf buf, BladeBlockPayload payload) {
         buf.writeBoolean(payload.blocking());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
