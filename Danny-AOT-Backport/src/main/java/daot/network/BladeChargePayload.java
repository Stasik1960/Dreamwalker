package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record BladeChargePayload(float multiplier) implements CustomPayload {
   public static final Id<BladeChargePayload> TYPE = new Id<>(new Identifier("dannys-aot", "blade_charge"));
   public static final PacketCodec<PacketByteBuf, BladeChargePayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, BladeChargePayload>() {
      public BladeChargePayload decode(PacketByteBuf buf) {
         return new BladeChargePayload(buf.readFloat());
      }

      public void encode(PacketByteBuf buf, BladeChargePayload payload) {
         buf.writeFloat(payload.multiplier());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
