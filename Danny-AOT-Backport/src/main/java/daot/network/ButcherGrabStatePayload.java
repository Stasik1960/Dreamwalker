package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ButcherGrabStatePayload(UUID firerId, boolean active, double dirX, double dirY, double dirZ, double distance) implements CustomPayload {
   public static final Id<ButcherGrabStatePayload> TYPE = new Id<>(new Identifier("dannys-aot", "butcher_grab_state"));
   public static final PacketCodec<PacketByteBuf, ButcherGrabStatePayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeUuid(p.firerId);
      buf.writeBoolean(p.active);
      buf.writeDouble(p.dirX);
      buf.writeDouble(p.dirY);
      buf.writeDouble(p.dirZ);
      buf.writeDouble(p.distance);
   }, buf -> new ButcherGrabStatePayload(buf.readUuid(), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
