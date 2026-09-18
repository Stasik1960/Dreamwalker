package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TargetModeSyncPayload(String mode) implements CustomPayload {
   public static final Id<TargetModeSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "target_mode_sync"));
   public static final PacketCodec<PacketByteBuf, TargetModeSyncPayload> STREAM_CODEC = PacketCodec.ofStatic(
      TargetModeSyncPayload::encode, TargetModeSyncPayload::decode
   );

   private static void encode(PacketByteBuf buf, TargetModeSyncPayload payload) {
      buf.writeString(payload.mode);
   }

   private static TargetModeSyncPayload decode(PacketByteBuf buf) {
      return new TargetModeSyncPayload(buf.readString());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
