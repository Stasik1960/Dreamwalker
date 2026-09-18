package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderFlyStatePayload(UUID playerUuid, boolean flying, boolean initialTakeoff) implements CustomPayload {
   public static final Id<HomelanderFlyStatePayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_fly_state"));
   public static final PacketCodec<PacketByteBuf, HomelanderFlyStatePayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeUuid(p.playerUuid);
      buf.writeBoolean(p.flying);
      buf.writeBoolean(p.initialTakeoff);
   }, buf -> new HomelanderFlyStatePayload(buf.readUuid(), buf.readBoolean(), buf.readBoolean()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
