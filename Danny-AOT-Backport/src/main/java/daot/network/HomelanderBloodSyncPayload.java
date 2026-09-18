package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderBloodSyncPayload(UUID playerUuid, int stage, long startedAtGameTime, long fadeStartedAtGameTime, int fadeDurationTicks)
   implements CustomPayload {
   public static final Id<HomelanderBloodSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_blood_sync"));
   public static final PacketCodec<PacketByteBuf, HomelanderBloodSyncPayload> STREAM_CODEC = PacketCodec.ofStatic(
      HomelanderBloodSyncPayload::encode, HomelanderBloodSyncPayload::decode
   );

   private static void encode(PacketByteBuf buf, HomelanderBloodSyncPayload payload) {
      buf.writeUuid(payload.playerUuid);
      buf.writeVarInt(payload.stage);
      buf.writeLong(payload.startedAtGameTime);
      buf.writeLong(payload.fadeStartedAtGameTime);
      buf.writeVarInt(payload.fadeDurationTicks);
   }

   private static HomelanderBloodSyncPayload decode(PacketByteBuf buf) {
      return new HomelanderBloodSyncPayload(buf.readUuid(), buf.readVarInt(), buf.readLong(), buf.readLong(), buf.readVarInt());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
