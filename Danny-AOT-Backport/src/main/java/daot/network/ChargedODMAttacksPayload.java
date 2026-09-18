package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ChargedODMAttacksPayload(boolean enabled) implements CustomPayload {
   public static final Id<ChargedODMAttacksPayload> TYPE = new Id<>(new Identifier("dannys-aot", "charged_odm_attacks"));
   public static final PacketCodec<PacketByteBuf, ChargedODMAttacksPayload> STREAM_CODEC = PacketCodec.ofStatic(
      ChargedODMAttacksPayload::write, ChargedODMAttacksPayload::read
   );

   private static ChargedODMAttacksPayload read(PacketByteBuf buf) {
      return new ChargedODMAttacksPayload(buf.readBoolean());
   }

   private static void write(PacketByteBuf buf, ChargedODMAttacksPayload payload) {
      buf.writeBoolean(payload.enabled);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
