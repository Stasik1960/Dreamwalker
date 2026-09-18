package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderFlyTogglePayload(boolean flying) implements CustomPayload {
   public static final Id<HomelanderFlyTogglePayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_fly_toggle"));
   public static final PacketCodec<PacketByteBuf, HomelanderFlyTogglePayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, p) -> buf.writeBoolean(p.flying), buf -> new HomelanderFlyTogglePayload(buf.readBoolean())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
