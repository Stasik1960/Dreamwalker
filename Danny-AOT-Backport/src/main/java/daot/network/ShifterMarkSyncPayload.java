package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ShifterMarkSyncPayload(UUID playerUuid, String markType, long dismountedAtGameTime, long fadeStartedAtGameTime) implements CustomPayload {
   public static final Id<ShifterMarkSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "shifter_mark_sync"));
   public static final PacketCodec<PacketByteBuf, ShifterMarkSyncPayload> STREAM_CODEC = PacketCodec.ofStatic(
      ShifterMarkSyncPayload::encode, ShifterMarkSyncPayload::decode
   );

   private static void encode(PacketByteBuf buf, ShifterMarkSyncPayload payload) {
      buf.writeUuid(payload.playerUuid);
      buf.writeString(payload.markType);
      buf.writeLong(payload.dismountedAtGameTime);
      buf.writeLong(payload.fadeStartedAtGameTime);
   }

   private static ShifterMarkSyncPayload decode(PacketByteBuf buf) {
      return new ShifterMarkSyncPayload(buf.readUuid(), buf.readString(), buf.readLong(), buf.readLong());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
