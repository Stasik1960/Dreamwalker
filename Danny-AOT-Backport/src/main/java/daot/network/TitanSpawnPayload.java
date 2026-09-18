package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TitanSpawnPayload(double x, double y, double z, int titanEntityId, int titanType) implements CustomPayload {
   public static final int TYPE_DEFAULT = 0;
   public static final int TYPE_ATTACK = 1;
   public static final int TYPE_ARMORED = 2;
   public static final int TYPE_FEMALE = 3;
   public static final int TYPE_BEAST = 4;
   public static final int TYPE_WARHAMMER = 5;
   public static final int TYPE_COLOSSAL = 6;
   public static final Id<TitanSpawnPayload> TYPE = new Id<>(new Identifier("dannys-aot", "titan_spawn"));
   public static final PacketCodec<PacketByteBuf, TitanSpawnPayload> STREAM_CODEC = PacketCodec.ofStatic(TitanSpawnPayload::encode, TitanSpawnPayload::decode);

   private static void encode(PacketByteBuf buf, TitanSpawnPayload payload) {
      buf.writeDouble(payload.x);
      buf.writeDouble(payload.y);
      buf.writeDouble(payload.z);
      buf.writeVarInt(payload.titanEntityId);
      buf.writeVarInt(payload.titanType);
   }

   private static TitanSpawnPayload decode(PacketByteBuf buf) {
      return new TitanSpawnPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
