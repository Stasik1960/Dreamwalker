package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderLaserDirectionBroadcastPayload(UUID firerId, double dirX, double dirY, double dirZ) implements CustomPayload {
   public static final Id<HomelanderLaserDirectionBroadcastPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_laser_direction"));
   public static final PacketCodec<PacketByteBuf, HomelanderLaserDirectionBroadcastPayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeUuid(p.firerId);
      buf.writeDouble(p.dirX);
      buf.writeDouble(p.dirY);
      buf.writeDouble(p.dirZ);
   }, buf -> new HomelanderLaserDirectionBroadcastPayload(buf.readUuid(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
