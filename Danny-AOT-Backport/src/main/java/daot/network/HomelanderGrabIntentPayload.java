package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record HomelanderGrabIntentPayload(boolean wantsGrab) implements CustomPayload {
   public static final Id<HomelanderGrabIntentPayload> TYPE = new Id<>(new Identifier("dannys-aot", "homelander_grab_intent"));
   public static final PacketCodec<PacketByteBuf, HomelanderGrabIntentPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, p) -> buf.writeBoolean(p.wantsGrab), buf -> new HomelanderGrabIntentPayload(buf.readBoolean())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
