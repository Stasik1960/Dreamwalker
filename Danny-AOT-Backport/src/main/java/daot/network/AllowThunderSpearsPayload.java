package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record AllowThunderSpearsPayload(boolean allowed) implements CustomPayload {
   public static final Id<AllowThunderSpearsPayload> TYPE = new Id<>(new Identifier("dannys-aot", "allow_thunder_spears"));
   public static final PacketCodec<PacketByteBuf, AllowThunderSpearsPayload> STREAM_CODEC = PacketCodec.ofStatic(
      AllowThunderSpearsPayload::write, AllowThunderSpearsPayload::read
   );

   private static AllowThunderSpearsPayload read(PacketByteBuf buf) {
      return new AllowThunderSpearsPayload(buf.readBoolean());
   }

   private static void write(PacketByteBuf buf, AllowThunderSpearsPayload payload) {
      buf.writeBoolean(payload.allowed);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
