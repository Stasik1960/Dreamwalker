package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.PacketCodecs;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record VillagerTransformPayload(int villagerEntityId) implements CustomPayload {
   public static final Id<VillagerTransformPayload> TYPE = new Id<>(new Identifier("dannys-aot", "villager_transform"));
   public static final PacketCodec<PacketByteBuf, VillagerTransformPayload> STREAM_CODEC = PacketCodec.tuple(
      PacketCodecs.VAR_INT, VillagerTransformPayload::villagerEntityId, VillagerTransformPayload::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
