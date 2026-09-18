package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.PacketCodecs;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ReversalSyncPayload(boolean reversed) implements CustomPayload {
   public static final Id<ReversalSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "reversal_sync"));
   public static final PacketCodec<PacketByteBuf, ReversalSyncPayload> STREAM_CODEC = PacketCodec.tuple(
      PacketCodecs.BOOL, ReversalSyncPayload::reversed, ReversalSyncPayload::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
