package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderLaserActivePayload(UUID playerUuid, boolean active) implements CustomPayload {
   public static final Id<HomelanderLaserActivePayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_laser_active"));
   public static final PacketCodec<PacketByteBuf, HomelanderLaserActivePayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeUuid(p.playerUuid);
      buf.writeBoolean(p.active);
   }, buf -> new HomelanderLaserActivePayload(buf.readUuid(), buf.readBoolean()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
