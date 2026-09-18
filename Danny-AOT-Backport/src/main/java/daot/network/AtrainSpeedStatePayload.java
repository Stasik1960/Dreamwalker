package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record AtrainSpeedStatePayload(UUID playerUuid, boolean active) implements CustomPayload {
   public static final Id<AtrainSpeedStatePayload> TYPE = new Id<>(new Identifier("dannys-aot", "atrain_speed_state"));
   public static final PacketCodec<PacketByteBuf, AtrainSpeedStatePayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeUuid(p.playerUuid);
      buf.writeBoolean(p.active);
   }, buf -> new AtrainSpeedStatePayload(buf.readUuid(), buf.readBoolean()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
