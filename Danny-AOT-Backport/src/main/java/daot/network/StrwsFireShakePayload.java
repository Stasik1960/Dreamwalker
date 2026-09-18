package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record StrwsFireShakePayload(float intensity) implements CustomPayload {
   public static final Id<StrwsFireShakePayload> TYPE = new Id<>(new Identifier("dannys-aot", "strws_fire_shake"));
   public static final PacketCodec<PacketByteBuf, StrwsFireShakePayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, StrwsFireShakePayload>() {
      public StrwsFireShakePayload decode(PacketByteBuf buf) {
         return new StrwsFireShakePayload(buf.readFloat());
      }

      public void encode(PacketByteBuf buf, StrwsFireShakePayload payload) {
         buf.writeFloat(payload.intensity());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
