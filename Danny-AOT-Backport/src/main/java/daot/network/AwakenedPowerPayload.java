package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record AwakenedPowerPayload() implements CustomPayload {
   public static final Id<AwakenedPowerPayload> TYPE = new Id<>(new Identifier("dannys-aot", "awakened_power"));
   public static final PacketCodec<PacketByteBuf, AwakenedPowerPayload> STREAM_CODEC = PacketCodec.unit(new AwakenedPowerPayload());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
