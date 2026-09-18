package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record APGAimSyncPayload(int playerId, boolean mainAiming, boolean offAiming) implements CustomPayload {
   public static final Id<APGAimSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "apg_aim_sync"));
   public static final PacketCodec<PacketByteBuf, APGAimSyncPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, APGAimSyncPayload>() {
      public APGAimSyncPayload decode(PacketByteBuf buf) {
         return new APGAimSyncPayload(buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
      }

      public void encode(PacketByteBuf buf, APGAimSyncPayload payload) {
         buf.writeVarInt(payload.playerId());
         buf.writeBoolean(payload.mainAiming());
         buf.writeBoolean(payload.offAiming());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
