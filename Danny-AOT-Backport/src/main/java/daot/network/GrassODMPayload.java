package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record GrassODMPayload(boolean allowed) implements CustomPayload {
   public static final Id<GrassODMPayload> TYPE = new Id<>(new Identifier("dannys-aot", "grass_odm"));
   public static final PacketCodec<PacketByteBuf, GrassODMPayload> STREAM_CODEC = PacketCodec.ofStatic(GrassODMPayload::write, GrassODMPayload::read);

   private static GrassODMPayload read(PacketByteBuf buf) {
      return new GrassODMPayload(buf.readBoolean());
   }

   private static void write(PacketByteBuf buf, GrassODMPayload payload) {
      buf.writeBoolean(payload.allowed);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
