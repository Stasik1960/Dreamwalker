package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record AllowODMPayload(boolean allowed) implements CustomPayload {
   public static final Id<AllowODMPayload> TYPE = new Id<>(new Identifier("dannys-aot", "allow_odm"));
   public static final PacketCodec<PacketByteBuf, AllowODMPayload> STREAM_CODEC = PacketCodec.ofStatic(AllowODMPayload::write, AllowODMPayload::read);

   private static AllowODMPayload read(PacketByteBuf buf) {
      return new AllowODMPayload(buf.readBoolean());
   }

   private static void write(PacketByteBuf buf, AllowODMPayload payload) {
      buf.writeBoolean(payload.allowed);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
