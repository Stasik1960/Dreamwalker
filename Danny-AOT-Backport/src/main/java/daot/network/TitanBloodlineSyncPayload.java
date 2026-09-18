package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TitanBloodlineSyncPayload(boolean active) implements CustomPayload {
   public static final Id<TitanBloodlineSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "titan_bloodline_sync"));
   public static final PacketCodec<PacketByteBuf, TitanBloodlineSyncPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, TitanBloodlineSyncPayload>() {
      public TitanBloodlineSyncPayload decode(PacketByteBuf buf) {
         return new TitanBloodlineSyncPayload(buf.readBoolean());
      }

      public void encode(PacketByteBuf buf, TitanBloodlineSyncPayload payload) {
         buf.writeBoolean(payload.active());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
