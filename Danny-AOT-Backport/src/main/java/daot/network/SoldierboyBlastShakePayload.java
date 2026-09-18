package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record SoldierboyBlastShakePayload(double x, double y, double z) implements CustomPayload {
   public static final Id<SoldierboyBlastShakePayload> TYPE = new Id<>(new Identifier("dannys-aot", "soldierboy_blast_shake"));
   public static final PacketCodec<PacketByteBuf, SoldierboyBlastShakePayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeDouble(p.x);
      buf.writeDouble(p.y);
      buf.writeDouble(p.z);
   }, buf -> new SoldierboyBlastShakePayload(buf.readDouble(), buf.readDouble(), buf.readDouble()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
