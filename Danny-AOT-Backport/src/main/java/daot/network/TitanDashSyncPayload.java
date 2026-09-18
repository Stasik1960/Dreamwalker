package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TitanDashSyncPayload(float charge, float maxCharge, int phase, float dirX, float dirY, float dirZ) implements CustomPayload {
   public static final Id<TitanDashSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "titan_dash_sync"));
   public static final PacketCodec<PacketByteBuf, TitanDashSyncPayload> STREAM_CODEC = PacketCodec.ofStatic(
      TitanDashSyncPayload::encode, TitanDashSyncPayload::decode
   );

   private static void encode(PacketByteBuf buf, TitanDashSyncPayload p) {
      buf.writeFloat(p.charge);
      buf.writeFloat(p.maxCharge);
      buf.writeVarInt(p.phase);
      buf.writeFloat(p.dirX);
      buf.writeFloat(p.dirY);
      buf.writeFloat(p.dirZ);
   }

   private static TitanDashSyncPayload decode(PacketByteBuf buf) {
      return new TitanDashSyncPayload(buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
