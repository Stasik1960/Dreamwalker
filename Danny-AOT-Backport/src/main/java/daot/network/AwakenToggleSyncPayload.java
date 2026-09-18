package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record AwakenToggleSyncPayload(UUID playerUuid, boolean active) implements CustomPayload {
   public static final Id<AwakenToggleSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "awaken_toggle_sync"));
   public static final PacketCodec<PacketByteBuf, AwakenToggleSyncPayload> STREAM_CODEC = PacketCodec.ofStatic(
      AwakenToggleSyncPayload::encode, AwakenToggleSyncPayload::decode
   );

   private static void encode(PacketByteBuf buf, AwakenToggleSyncPayload payload) {
      buf.writeUuid(payload.playerUuid);
      buf.writeBoolean(payload.active);
   }

   private static AwakenToggleSyncPayload decode(PacketByteBuf buf) {
      return new AwakenToggleSyncPayload(buf.readUuid(), buf.readBoolean());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
