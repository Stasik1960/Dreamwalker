package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record VillagerTransformSpawnPayload(double x, double y, double z, int titanEntityId) implements CustomPayload {
   public static final Id<VillagerTransformSpawnPayload> TYPE = new Id<>(new Identifier("dannys-aot", "villager_transform_spawn"));
   public static final PacketCodec<PacketByteBuf, VillagerTransformSpawnPayload> STREAM_CODEC = PacketCodec.ofStatic(
      VillagerTransformSpawnPayload::encode, VillagerTransformSpawnPayload::decode
   );

   private static void encode(PacketByteBuf buf, VillagerTransformSpawnPayload payload) {
      buf.writeDouble(payload.x);
      buf.writeDouble(payload.y);
      buf.writeDouble(payload.z);
      buf.writeVarInt(payload.titanEntityId);
   }

   private static VillagerTransformSpawnPayload decode(PacketByteBuf buf) {
      return new VillagerTransformSpawnPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
