package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ColossalHandBoneSyncPayload(int entityId, double handX, double handY, double handZ) implements CustomPayload {
   public static final Id<ColossalHandBoneSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "colossal_hand_bone_sync"));
   public static final PacketCodec<PacketByteBuf, ColossalHandBoneSyncPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, ColossalHandBoneSyncPayload>() {
      public ColossalHandBoneSyncPayload decode(PacketByteBuf buf) {
         return new ColossalHandBoneSyncPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
      }

      public void encode(PacketByteBuf buf, ColossalHandBoneSyncPayload payload) {
         buf.writeVarInt(payload.entityId);
         buf.writeDouble(payload.handX);
         buf.writeDouble(payload.handY);
         buf.writeDouble(payload.handZ);
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
