package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderLaserPayload(double dirX, double dirY, double dirZ) implements CustomPayload {
   public static final Id<HomelanderLaserPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_laser"));
   public static final PacketCodec<PacketByteBuf, HomelanderLaserPayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeDouble(p.dirX);
      buf.writeDouble(p.dirY);
      buf.writeDouble(p.dirZ);
   }, buf -> new HomelanderLaserPayload(buf.readDouble(), buf.readDouble(), buf.readDouble()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
