package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record GrabBoneSyncPayload(int entityId, double grabX, double grabY, double grabZ) implements CustomPayload {
   public static final Id<GrabBoneSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "grab_bone_sync"));
   public static final PacketCodec<PacketByteBuf, GrabBoneSyncPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, GrabBoneSyncPayload>() {
      public GrabBoneSyncPayload decode(PacketByteBuf buf) {
         return new GrabBoneSyncPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
      }

      public void encode(PacketByteBuf buf, GrabBoneSyncPayload payload) {
         buf.writeVarInt(payload.entityId);
         buf.writeDouble(payload.grabX);
         buf.writeDouble(payload.grabY);
         buf.writeDouble(payload.grabZ);
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
