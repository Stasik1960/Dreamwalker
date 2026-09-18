package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record FogSyncPayload(boolean fogActive, int[] breachedWalls, int[] breachedDistricts) implements CustomPayload {
   public static final Id<FogSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "fog_sync"));
   public static final PacketCodec<PacketByteBuf, FogSyncPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, FogSyncPayload>() {
      public FogSyncPayload decode(PacketByteBuf buf) {
         boolean active = buf.readBoolean();
         int[] walls = buf.readIntArray();
         int[] districts = buf.readIntArray();
         return new FogSyncPayload(active, walls, districts);
      }

      public void encode(PacketByteBuf buf, FogSyncPayload payload) {
         buf.writeBoolean(payload.fogActive());
         buf.writeIntArray(payload.breachedWalls());
         buf.writeIntArray(payload.breachedDistricts());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
