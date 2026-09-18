package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record AnimBroadcastPayload(int action) implements CustomPayload {
   public static final Id<AnimBroadcastPayload> TYPE = new Id<>(new Identifier("dannys-aot", "anim_broadcast"));
   public static final PacketCodec<PacketByteBuf, AnimBroadcastPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, payload) -> buf.writeVarInt(payload.action()), buf -> new AnimBroadcastPayload(buf.readVarInt())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
