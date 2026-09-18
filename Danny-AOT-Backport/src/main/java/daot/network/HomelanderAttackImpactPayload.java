package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderAttackImpactPayload(byte attackType) implements CustomPayload {
   public static final Id<HomelanderAttackImpactPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_attack_impact"));
   public static final PacketCodec<PacketByteBuf, HomelanderAttackImpactPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, p) -> buf.writeByte(p.attackType), buf -> new HomelanderAttackImpactPayload(buf.readByte())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
