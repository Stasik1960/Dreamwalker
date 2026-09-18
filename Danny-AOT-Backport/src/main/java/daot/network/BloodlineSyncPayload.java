package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record BloodlineSyncPayload(UUID playerUuid, int bloodlineOrdinal) implements CustomPayload {
   public static final Id<BloodlineSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "bloodline_sync"));
   public static final PacketCodec<PacketByteBuf, BloodlineSyncPayload> STREAM_CODEC = PacketCodec.ofStatic(
      BloodlineSyncPayload::encode, BloodlineSyncPayload::decode
   );

   private static void encode(PacketByteBuf buf, BloodlineSyncPayload payload) {
      buf.writeUuid(payload.playerUuid);
      buf.writeInt(payload.bloodlineOrdinal);
   }

   private static BloodlineSyncPayload decode(PacketByteBuf buf) {
      return new BloodlineSyncPayload(buf.readUuid(), buf.readInt());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
