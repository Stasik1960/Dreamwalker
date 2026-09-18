package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record APGFireShakePayload(float intensity) implements CustomPayload {
   public static final Id<APGFireShakePayload> TYPE = new Id<>(new Identifier("dannys-aot", "apg_fire_shake"));
   public static final PacketCodec<PacketByteBuf, APGFireShakePayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, APGFireShakePayload>() {
      public APGFireShakePayload decode(PacketByteBuf buf) {
         return new APGFireShakePayload(buf.readFloat());
      }

      public void encode(PacketByteBuf buf, APGFireShakePayload payload) {
         buf.writeFloat(payload.intensity());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
