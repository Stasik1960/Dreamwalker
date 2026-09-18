package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ShiftShakePayload(double x, double y, double z) implements CustomPayload {
   public static final Id<ShiftShakePayload> TYPE = new Id<>(new Identifier("dannys-aot", "shift_shake"));
   public static final PacketCodec<PacketByteBuf, ShiftShakePayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeDouble(p.x);
      buf.writeDouble(p.y);
      buf.writeDouble(p.z);
   }, buf -> new ShiftShakePayload(buf.readDouble(), buf.readDouble(), buf.readDouble()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
