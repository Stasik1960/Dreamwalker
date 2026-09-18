package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderSonicBoomPayload(double x, double y, double z, double dirX, double dirY, double dirZ) implements CustomPayload {
   public static final Id<HomelanderSonicBoomPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_sonic_boom"));
   public static final PacketCodec<PacketByteBuf, HomelanderSonicBoomPayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeDouble(p.x);
      buf.writeDouble(p.y);
      buf.writeDouble(p.z);
      buf.writeDouble(p.dirX);
      buf.writeDouble(p.dirY);
      buf.writeDouble(p.dirZ);
   }, buf -> new HomelanderSonicBoomPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
