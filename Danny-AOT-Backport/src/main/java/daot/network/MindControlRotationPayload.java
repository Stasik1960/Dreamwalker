package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record MindControlRotationPayload(float yaw, float pitch) implements CustomPayload {
   public static final Id<MindControlRotationPayload> TYPE = new Id<>(new Identifier("dannys-aot", "mind_control_rot"));
   public static final PacketCodec<PacketByteBuf, MindControlRotationPayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeFloat(p.yaw);
      buf.writeFloat(p.pitch);
   }, buf -> new MindControlRotationPayload(buf.readFloat(), buf.readFloat()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
