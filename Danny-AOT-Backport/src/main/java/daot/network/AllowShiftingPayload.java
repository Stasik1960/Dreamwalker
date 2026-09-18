package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record AllowShiftingPayload(boolean allowed) implements CustomPayload {
   public static final Id<AllowShiftingPayload> TYPE = new Id<>(new Identifier("dannys-aot", "allow_shifting"));
   public static final PacketCodec<PacketByteBuf, AllowShiftingPayload> STREAM_CODEC = PacketCodec.ofStatic(
      AllowShiftingPayload::write, AllowShiftingPayload::read
   );

   private static AllowShiftingPayload read(PacketByteBuf buf) {
      return new AllowShiftingPayload(buf.readBoolean());
   }

   private static void write(PacketByteBuf buf, AllowShiftingPayload payload) {
      buf.writeBoolean(payload.allowed);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
