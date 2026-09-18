package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record JawPounceTargetPayload(int entityId) implements CustomPayload {
   public static final Id<JawPounceTargetPayload> TYPE = new Id<>(new Identifier("dannys-aot", "jaw_pounce_target"));
   public static final PacketCodec<PacketByteBuf, JawPounceTargetPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, p) -> buf.writeVarInt(p.entityId), buf -> new JawPounceTargetPayload(buf.readVarInt())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
