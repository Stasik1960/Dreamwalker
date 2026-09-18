package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record GeassControlPayload(int commandType, int targetEntityId) implements CustomPayload {
   public static final int CMD_RELEASED = 0;
   public static final int CMD_FOLLOW = 1;
   public static final int CMD_KILL = 2;
   public static final int CMD_STOP_BREATHING = 3;
   public static final int CMD_FREEZE = 4;
   public static final int CMD_MIND_CONTROL = 5;
   public static final int CMD_MIND_CONTROL_CONTROLLER = 7;
   public static final Id<GeassControlPayload> TYPE = new Id<>(new Identifier("dannys-aot", "geass_control"));
   public static final PacketCodec<PacketByteBuf, GeassControlPayload> STREAM_CODEC = PacketCodec.ofStatic(
      GeassControlPayload::encode, GeassControlPayload::decode
   );

   private static void encode(PacketByteBuf buf, GeassControlPayload payload) {
      buf.writeInt(payload.commandType);
      buf.writeInt(payload.targetEntityId);
   }

   private static GeassControlPayload decode(PacketByteBuf buf) {
      return new GeassControlPayload(buf.readInt(), buf.readInt());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
