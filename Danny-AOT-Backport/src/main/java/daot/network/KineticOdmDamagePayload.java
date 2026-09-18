package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record KineticOdmDamagePayload(boolean enabled) implements CustomPayload {
   public static final Id<KineticOdmDamagePayload> TYPE = new Id<>(new Identifier("dannys-aot", "kinetic_odm_damage"));
   public static final PacketCodec<PacketByteBuf, KineticOdmDamagePayload> STREAM_CODEC = PacketCodec.ofStatic(
      KineticOdmDamagePayload::write, KineticOdmDamagePayload::read
   );

   private static KineticOdmDamagePayload read(PacketByteBuf buf) {
      return new KineticOdmDamagePayload(buf.readBoolean());
   }

   private static void write(PacketByteBuf buf, KineticOdmDamagePayload payload) {
      buf.writeBoolean(payload.enabled);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
