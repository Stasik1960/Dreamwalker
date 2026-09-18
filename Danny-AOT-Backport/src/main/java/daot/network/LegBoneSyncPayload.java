package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record LegBoneSyncPayload(int entityId, double leftX, double leftY, double leftZ, double rightX, double rightY, double rightZ) implements CustomPayload {
   public static final Id<LegBoneSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "leg_bone_sync"));
   public static final PacketCodec<PacketByteBuf, LegBoneSyncPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, LegBoneSyncPayload>() {
      public LegBoneSyncPayload decode(PacketByteBuf buf) {
         return new LegBoneSyncPayload(
            buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()
         );
      }

      public void encode(PacketByteBuf buf, LegBoneSyncPayload payload) {
         buf.writeVarInt(payload.entityId);
         buf.writeDouble(payload.leftX);
         buf.writeDouble(payload.leftY);
         buf.writeDouble(payload.leftZ);
         buf.writeDouble(payload.rightX);
         buf.writeDouble(payload.rightY);
         buf.writeDouble(payload.rightZ);
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
