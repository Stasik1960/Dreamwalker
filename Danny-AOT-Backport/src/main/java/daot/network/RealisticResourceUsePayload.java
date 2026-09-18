package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record RealisticResourceUsePayload(boolean enabled) implements CustomPayload {
   public static final Id<RealisticResourceUsePayload> TYPE = new Id<>(new Identifier("dannys-aot", "realistic_resource_use"));
   public static final PacketCodec<PacketByteBuf, RealisticResourceUsePayload> STREAM_CODEC = PacketCodec.ofStatic(
      RealisticResourceUsePayload::write, RealisticResourceUsePayload::read
   );

   private static RealisticResourceUsePayload read(PacketByteBuf buf) {
      return new RealisticResourceUsePayload(buf.readBoolean());
   }

   private static void write(PacketByteBuf buf, RealisticResourceUsePayload payload) {
      buf.writeBoolean(payload.enabled);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
