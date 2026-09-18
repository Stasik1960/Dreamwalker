package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record MindControlActionPayload(int action, int entityId, long blockPosLong, int direction, int hand) implements CustomPayload {
   public static final Id<MindControlActionPayload> TYPE = new Id<>(new Identifier("dannys-aot", "mind_control_action"));
   public static final PacketCodec<PacketByteBuf, MindControlActionPayload> STREAM_CODEC = PacketCodec.ofStatic(
      MindControlActionPayload::encode, MindControlActionPayload::decode
   );

   private static void encode(PacketByteBuf buf, MindControlActionPayload p) {
      buf.writeVarInt(p.action);
      buf.writeVarInt(p.entityId);
      buf.writeLong(p.blockPosLong);
      buf.writeVarInt(p.direction);
      buf.writeVarInt(p.hand);
   }

   private static MindControlActionPayload decode(PacketByteBuf buf) {
      return new MindControlActionPayload(buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readVarInt());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
