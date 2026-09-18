package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ButcherTentacleBroadcastPayload(UUID firerId, long startTick, double dirX, double dirY, double dirZ) implements CustomPayload {
   public static final Id<ButcherTentacleBroadcastPayload> TYPE = new Id<>(new Identifier("dannys-aot", "butcher_tentacle_broadcast"));
   public static final PacketCodec<PacketByteBuf, ButcherTentacleBroadcastPayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeUuid(p.firerId);
      buf.writeLong(p.startTick);
      buf.writeDouble(p.dirX);
      buf.writeDouble(p.dirY);
      buf.writeDouble(p.dirZ);
   }, buf -> new ButcherTentacleBroadcastPayload(buf.readUuid(), buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
