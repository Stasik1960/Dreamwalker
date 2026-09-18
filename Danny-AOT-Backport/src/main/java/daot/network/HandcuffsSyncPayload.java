package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HandcuffsSyncPayload(UUID playerUuid, boolean cuffed, boolean hasShifter) implements CustomPayload {
   public static final Id<HandcuffsSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "handcuffs_sync"));
   public static final PacketCodec<PacketByteBuf, HandcuffsSyncPayload> STREAM_CODEC = PacketCodec.ofStatic(
      HandcuffsSyncPayload::encode, HandcuffsSyncPayload::decode
   );

   private static void encode(PacketByteBuf buf, HandcuffsSyncPayload payload) {
      buf.writeUuid(payload.playerUuid);
      buf.writeBoolean(payload.cuffed);
      buf.writeBoolean(payload.hasShifter);
   }

   private static HandcuffsSyncPayload decode(PacketByteBuf buf) {
      return new HandcuffsSyncPayload(buf.readUuid(), buf.readBoolean(), buf.readBoolean());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
