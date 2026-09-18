package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record KineticOdmImpactPayload(float speed) implements CustomPayload {
   public static final Id<KineticOdmImpactPayload> TYPE = new Id<>(new Identifier("dannys-aot", "kinetic_odm_impact"));
   public static final PacketCodec<PacketByteBuf, KineticOdmImpactPayload> STREAM_CODEC = PacketCodec.ofStatic(
      KineticOdmImpactPayload::write, KineticOdmImpactPayload::read
   );

   private static KineticOdmImpactPayload read(PacketByteBuf buf) {
      return new KineticOdmImpactPayload(buf.readFloat());
   }

   private static void write(PacketByteBuf buf, KineticOdmImpactPayload payload) {
      buf.writeFloat(payload.speed);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
