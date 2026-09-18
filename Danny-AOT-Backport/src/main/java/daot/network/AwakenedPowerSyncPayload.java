package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record AwakenedPowerSyncPayload(float charge, float maxCharge, boolean active) implements CustomPayload {
   public static final Id<AwakenedPowerSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "awakened_power_sync"));
   public static final PacketCodec<PacketByteBuf, AwakenedPowerSyncPayload> STREAM_CODEC = PacketCodec.ofStatic(
      AwakenedPowerSyncPayload::encode, AwakenedPowerSyncPayload::decode
   );

   private static void encode(PacketByteBuf buf, AwakenedPowerSyncPayload payload) {
      buf.writeFloat(payload.charge);
      buf.writeFloat(payload.maxCharge);
      buf.writeBoolean(payload.active);
   }

   private static AwakenedPowerSyncPayload decode(PacketByteBuf buf) {
      return new AwakenedPowerSyncPayload(buf.readFloat(), buf.readFloat(), buf.readBoolean());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
