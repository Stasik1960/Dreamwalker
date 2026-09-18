package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record SoldierboyChargeStatePayload(UUID playerUuid, byte ability, boolean charging) implements CustomPayload {
   public static final Id<SoldierboyChargeStatePayload> TYPE = new Id<>(new Identifier("dannys-aot", "soldierboy_charge_state"));
   public static final PacketCodec<PacketByteBuf, SoldierboyChargeStatePayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeUuid(p.playerUuid);
      buf.writeByte(p.ability);
      buf.writeBoolean(p.charging);
   }, buf -> new SoldierboyChargeStatePayload(buf.readUuid(), buf.readByte(), buf.readBoolean()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
