package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderGrabRotationSyncPayload(float yaw, float pitch) implements CustomPayload {
   public static final Id<HomelanderGrabRotationSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_grab_rot_sync"));
   public static final PacketCodec<PacketByteBuf, HomelanderGrabRotationSyncPayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeFloat(p.yaw);
      buf.writeFloat(p.pitch);
   }, buf -> new HomelanderGrabRotationSyncPayload(buf.readFloat(), buf.readFloat()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
