package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TitanImpactShakePayload(double x, double y, double z, float intensity) implements CustomPayload {
   public static final Id<TitanImpactShakePayload> TYPE = new Id<>(new Identifier("dannys-aot", "titan_impact_shake"));
   public static final PacketCodec<PacketByteBuf, TitanImpactShakePayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeDouble(p.x);
      buf.writeDouble(p.y);
      buf.writeDouble(p.z);
      buf.writeFloat(p.intensity);
   }, buf -> new TitanImpactShakePayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
