package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.PacketCodecs;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record FreezeVignetteSyncPayload(boolean frozen) implements CustomPayload {
   public static final Id<FreezeVignetteSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "freeze_vignette_sync"));
   public static final PacketCodec<PacketByteBuf, FreezeVignetteSyncPayload> STREAM_CODEC = PacketCodec.tuple(
      PacketCodecs.BOOL, FreezeVignetteSyncPayload::frozen, FreezeVignetteSyncPayload::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
