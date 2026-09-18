package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ConfigSyncPayload(
   long dualHookEaseTime,
   int gasTickInterval,
   int gasConsumptionNormal,
   int gasConsumptionBoost,
   double basePullSpeed,
   double dualHookPullMultiplier,
   double dualHookBoostPullMultiplier,
   double orbitPullMultiplier,
   double baseOrbitSpeed,
   double dualHookOrbitMultiplier,
   double upwardLift,
   double boostPullMultiplier,
   double boostOrbitMultiplier,
   double boostRampRate,
   double boostDecayRate,
   double maxHookDistance,
   long momentumPreserveTime,
   double flightSoundVelocityThreshold
) implements CustomPayload {
   public static final Id<ConfigSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "config_sync"));
   public static final PacketCodec<PacketByteBuf, ConfigSyncPayload> STREAM_CODEC = PacketCodec.ofStatic(ConfigSyncPayload::write, ConfigSyncPayload::read);

   private static ConfigSyncPayload read(PacketByteBuf buf) {
      return new ConfigSyncPayload(
         buf.readLong(),
         buf.readInt(),
         buf.readInt(),
         buf.readInt(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readLong(),
         buf.readDouble()
      );
   }

   private static void write(PacketByteBuf buf, ConfigSyncPayload payload) {
      buf.writeLong(payload.dualHookEaseTime);
      buf.writeInt(payload.gasTickInterval);
      buf.writeInt(payload.gasConsumptionNormal);
      buf.writeInt(payload.gasConsumptionBoost);
      buf.writeDouble(payload.basePullSpeed);
      buf.writeDouble(payload.dualHookPullMultiplier);
      buf.writeDouble(payload.dualHookBoostPullMultiplier);
      buf.writeDouble(payload.orbitPullMultiplier);
      buf.writeDouble(payload.baseOrbitSpeed);
      buf.writeDouble(payload.dualHookOrbitMultiplier);
      buf.writeDouble(payload.upwardLift);
      buf.writeDouble(payload.boostPullMultiplier);
      buf.writeDouble(payload.boostOrbitMultiplier);
      buf.writeDouble(payload.boostRampRate);
      buf.writeDouble(payload.boostDecayRate);
      buf.writeDouble(payload.maxHookDistance);
      buf.writeLong(payload.momentumPreserveTime);
      buf.writeDouble(payload.flightSoundVelocityThreshold);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
