package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record SoldierboyChargePayload(byte ability, byte action, float chargeProgress, double dirX, double dirY, double dirZ) implements CustomPayload {
   public static final byte ABILITY_CHEST_BLAST = 0;
   public static final byte ABILITY_CHEST_LASER = 1;
   public static final byte ACTION_START = 0;
   public static final byte ACTION_RELEASE = 1;
   public static final byte ACTION_FIRE_TICK = 2;
   public static final Id<SoldierboyChargePayload> TYPE = new Id<>(new Identifier("dannys-aot", "soldierboy_charge"));
   public static final PacketCodec<PacketByteBuf, SoldierboyChargePayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeByte(p.ability);
      buf.writeByte(p.action);
      buf.writeFloat(p.chargeProgress);
      buf.writeDouble(p.dirX);
      buf.writeDouble(p.dirY);
      buf.writeDouble(p.dirZ);
   }, buf -> new SoldierboyChargePayload(buf.readByte(), buf.readByte(), buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
