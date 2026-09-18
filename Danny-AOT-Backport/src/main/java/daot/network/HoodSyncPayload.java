package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HoodSyncPayload(UUID playerUuid, boolean hoodUp) implements CustomPayload {
   public static final Id<HoodSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "hood_sync"));
   public static final PacketCodec<PacketByteBuf, HoodSyncPayload> STREAM_CODEC = PacketCodec.ofStatic(HoodSyncPayload::encode, HoodSyncPayload::decode);

   private static void encode(PacketByteBuf buf, HoodSyncPayload payload) {
      buf.writeUuid(payload.playerUuid);
      buf.writeBoolean(payload.hoodUp);
   }

   private static HoodSyncPayload decode(PacketByteBuf buf) {
      return new HoodSyncPayload(buf.readUuid(), buf.readBoolean());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
