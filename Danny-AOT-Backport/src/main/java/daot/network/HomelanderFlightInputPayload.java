package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderFlightInputPayload(byte bits) implements CustomPayload {
   public static final int BIT_FORWARD = 1;
   public static final int BIT_BACKWARD = 2;
   public static final int BIT_LEFT = 4;
   public static final int BIT_RIGHT = 8;
   public static final int BIT_JUMPING = 16;
   public static final int BIT_SHIFTING = 32;
   public static final int BIT_SPRINTING = 64;
   public static final Id<HomelanderFlightInputPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_flight_input"));
   public static final PacketCodec<PacketByteBuf, HomelanderFlightInputPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, p) -> buf.writeByte(p.bits), buf -> new HomelanderFlightInputPayload(buf.readByte())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
