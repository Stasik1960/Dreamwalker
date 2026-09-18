package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.PacketCodecs;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record DenialSyncPayload(boolean denied) implements CustomPayload {
   public static final Id<DenialSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "denial_sync"));
   public static final PacketCodec<PacketByteBuf, DenialSyncPayload> STREAM_CODEC = PacketCodec.tuple(
      PacketCodecs.BOOL, DenialSyncPayload::denied, DenialSyncPayload::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
