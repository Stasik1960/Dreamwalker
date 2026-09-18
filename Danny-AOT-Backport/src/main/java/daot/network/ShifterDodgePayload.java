package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ShifterDodgePayload(int direction) implements CustomPayload {
   public static final Id<ShifterDodgePayload> TYPE = new Id<>(new Identifier("dannys-aot", "shifter_dodge"));
   public static final PacketCodec<PacketByteBuf, ShifterDodgePayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, payload) -> buf.writeInt(payload.direction), buf -> new ShifterDodgePayload(buf.readInt())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
