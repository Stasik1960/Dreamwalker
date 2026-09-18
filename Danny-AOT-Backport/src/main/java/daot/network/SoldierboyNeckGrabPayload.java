package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record SoldierboyNeckGrabPayload(byte action) implements CustomPayload {
   public static final byte ACTION_X_PRESS = 0;
   public static final byte ACTION_PUNCH = 2;
   public static final Id<SoldierboyNeckGrabPayload> TYPE = new Id<>(new Identifier("dannys-aot", "soldierboy_neck_grab"));
   public static final PacketCodec<PacketByteBuf, SoldierboyNeckGrabPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, p) -> buf.writeByte(p.action), buf -> new SoldierboyNeckGrabPayload(buf.readByte())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
