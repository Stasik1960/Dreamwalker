package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record AtrainSpeedTogglePayload(boolean active) implements CustomPayload {
   public static final Id<AtrainSpeedTogglePayload> TYPE = new Id<>(new Identifier("dannys-aot", "atrain_speed_toggle"));
   public static final PacketCodec<PacketByteBuf, AtrainSpeedTogglePayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, p) -> buf.writeBoolean(p.active), buf -> new AtrainSpeedTogglePayload(buf.readBoolean())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
