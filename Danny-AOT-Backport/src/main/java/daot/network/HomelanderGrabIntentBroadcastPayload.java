package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderGrabIntentBroadcastPayload(UUID playerUuid, boolean wantsGrab) implements CustomPayload {
   public static final Id<HomelanderGrabIntentBroadcastPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_grab_intent_sync"));
   public static final PacketCodec<PacketByteBuf, HomelanderGrabIntentBroadcastPayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeUuid(p.playerUuid);
      buf.writeBoolean(p.wantsGrab);
   }, buf -> new HomelanderGrabIntentBroadcastPayload(buf.readUuid(), buf.readBoolean()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
