package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record MindControlSprintPayload(float forward, float strafe, boolean jumping, boolean sprinting, boolean shifting, boolean inventoryOpen)
   implements CustomPayload {
   public static final Id<MindControlSprintPayload> TYPE = new Id<>(new Identifier("dannys-aot", "mind_control_sprint"));
   public static final PacketCodec<PacketByteBuf, MindControlSprintPayload> STREAM_CODEC = PacketCodec.ofStatic((buf, payload) -> {
      buf.writeFloat(payload.forward);
      buf.writeFloat(payload.strafe);
      buf.writeBoolean(payload.jumping);
      buf.writeBoolean(payload.sprinting);
      buf.writeBoolean(payload.shifting);
      buf.writeBoolean(payload.inventoryOpen);
   }, buf -> new MindControlSprintPayload(buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
