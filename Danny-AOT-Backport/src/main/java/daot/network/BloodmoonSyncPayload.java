package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record BloodmoonSyncPayload(boolean active) implements CustomPayload {
   public static final Id<BloodmoonSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "bloodmoon_sync"));
   public static final PacketCodec<PacketByteBuf, BloodmoonSyncPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, BloodmoonSyncPayload>() {
      public BloodmoonSyncPayload decode(PacketByteBuf buf) {
         return new BloodmoonSyncPayload(buf.readBoolean());
      }

      public void encode(PacketByteBuf buf, BloodmoonSyncPayload payload) {
         buf.writeBoolean(payload.active());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
