package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderAttackPayload(byte attackType) implements CustomPayload {
   public static final byte TYPE_FLY_ATTACK = 0;
   public static final byte TYPE_ATTACK_1 = 1;
   public static final byte TYPE_ATTACK_2 = 2;
   public static final Id<HomelanderAttackPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_attack"));
   public static final PacketCodec<PacketByteBuf, HomelanderAttackPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, p) -> buf.writeByte(p.attackType), buf -> new HomelanderAttackPayload(buf.readByte())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
