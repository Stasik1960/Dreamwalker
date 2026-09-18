package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ODMJamPayload(int durationMs, boolean releaseHooks) implements CustomPayload {
   public static final Id<ODMJamPayload> TYPE = new Id<>(new Identifier("dannys-aot", "odm_jam"));
   public static final PacketCodec<PacketByteBuf, ODMJamPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, ODMJamPayload>() {
      public ODMJamPayload decode(PacketByteBuf buf) {
         return new ODMJamPayload(buf.readVarInt(), buf.readBoolean());
      }

      public void encode(PacketByteBuf buf, ODMJamPayload payload) {
         buf.writeVarInt(payload.durationMs);
         buf.writeBoolean(payload.releaseHooks);
      }
   };

   public ODMJamPayload(int durationMs) {
      this(durationMs, false);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
