package daot.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record SoldierboyNeckGrabStatePayload(UUID grabberUuid, int victimEntityId, boolean active) implements CustomPayload {
   public static final Id<SoldierboyNeckGrabStatePayload> TYPE = new Id<>(new Identifier("dannys-aot", "soldierboy_neck_grab_state"));
   public static final PacketCodec<PacketByteBuf, SoldierboyNeckGrabStatePayload> STREAM_CODEC = PacketCodec.ofStatic((buf, p) -> {
      buf.writeUuid(p.grabberUuid);
      buf.writeInt(p.victimEntityId);
      buf.writeBoolean(p.active);
   }, buf -> new SoldierboyNeckGrabStatePayload(buf.readUuid(), buf.readInt(), buf.readBoolean()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
