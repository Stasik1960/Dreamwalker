package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TitanSprintPayload(boolean sprinting) implements CustomPayload {
   public static final Id<TitanSprintPayload> TYPE = new Id<>(new Identifier("dannys-aot", "titan_sprint"));
   public static final PacketCodec<PacketByteBuf, TitanSprintPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, payload) -> buf.writeBoolean(payload.sprinting()), buf -> new TitanSprintPayload(buf.readBoolean())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
