package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HitboxBoneSyncPayload(int entityId, double napeX, double napeY, double napeZ, double eyeX, double eyeY, double eyeZ) implements CustomPayload {
   public static final Id<HitboxBoneSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "hitbox_bone_sync"));
   public static final PacketCodec<PacketByteBuf, HitboxBoneSyncPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, HitboxBoneSyncPayload>() {
      public HitboxBoneSyncPayload decode(PacketByteBuf buf) {
         return new HitboxBoneSyncPayload(
            buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()
         );
      }

      public void encode(PacketByteBuf buf, HitboxBoneSyncPayload payload) {
         buf.writeVarInt(payload.entityId);
         buf.writeDouble(payload.napeX);
         buf.writeDouble(payload.napeY);
         buf.writeDouble(payload.napeZ);
         buf.writeDouble(payload.eyeX);
         buf.writeDouble(payload.eyeY);
         buf.writeDouble(payload.eyeZ);
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
