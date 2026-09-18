package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record MindControlRotationSyncPayload(float yaw, float pitch) implements CustomPayload {
   public static final Id<MindControlRotationSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "mind_control_rot_sync"));
   public static final PacketCodec<PacketByteBuf, MindControlRotationSyncPayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeFloat(p.yaw);
      buf.writeFloat(p.pitch);
   }, buf -> new MindControlRotationSyncPayload(buf.readFloat(), buf.readFloat()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
