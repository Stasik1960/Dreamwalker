package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderAttackBroadcastPayload(UUID attackerUuid, byte attackType) implements CustomPayload {
   public static final Id<HomelanderAttackBroadcastPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_attack_sync"));
   public static final PacketCodec<PacketByteBuf, HomelanderAttackBroadcastPayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeUuid(p.attackerUuid);
      buf.writeByte(p.attackType);
   }, buf -> new HomelanderAttackBroadcastPayload(buf.readUuid(), buf.readByte()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
