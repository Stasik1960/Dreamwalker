package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderFlightInputBroadcastPayload(UUID playerUuid, byte bits) implements CustomPayload {
   public static final Id<HomelanderFlightInputBroadcastPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_flight_input_sync"));
   public static final PacketCodec<PacketByteBuf, HomelanderFlightInputBroadcastPayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeUuid(p.playerUuid);
      buf.writeByte(p.bits);
   }, buf -> new HomelanderFlightInputBroadcastPayload(buf.readUuid(), buf.readByte()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
