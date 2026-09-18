package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderGrabSyncPayload(UUID grabberUuid, int victimEntityId) implements CustomPayload {
   public static final int NO_VICTIM = -1;
   public static final Id<HomelanderGrabSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_grab_sync"));
   public static final PacketCodec<PacketByteBuf, HomelanderGrabSyncPayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeUuid(p.grabberUuid);
      buf.writeVarInt(p.victimEntityId);
   }, buf -> new HomelanderGrabSyncPayload(buf.readUuid(), buf.readVarInt()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
