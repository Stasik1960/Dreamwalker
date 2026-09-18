package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HammerBoneSyncPayload(int entityId, double hammerX, double hammerY, double hammerZ) implements CustomPayload {
   public static final Id<HammerBoneSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "hammer_bone_sync"));
   public static final PacketCodec<PacketByteBuf, HammerBoneSyncPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, HammerBoneSyncPayload>() {
      public HammerBoneSyncPayload decode(PacketByteBuf buf) {
         return new HammerBoneSyncPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
      }

      public void encode(PacketByteBuf buf, HammerBoneSyncPayload payload) {
         buf.writeVarInt(payload.entityId);
         buf.writeDouble(payload.hammerX);
         buf.writeDouble(payload.hammerY);
         buf.writeDouble(payload.hammerZ);
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
