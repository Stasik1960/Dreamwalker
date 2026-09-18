package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ShiftLightningPayload(int entityId) implements CustomPayload {
   public static final Id<ShiftLightningPayload> TYPE = new Id<>(new Identifier("dannys-aot", "shift_lightning"));
   public static final PacketCodec<PacketByteBuf, ShiftLightningPayload> STREAM_CODEC = PacketCodec.ofStatic(
      ShiftLightningPayload::encode, ShiftLightningPayload::decode
   );

   private static void encode(PacketByteBuf buf, ShiftLightningPayload payload) {
      buf.writeVarInt(payload.entityId);
   }

   private static ShiftLightningPayload decode(PacketByteBuf buf) {
      return new ShiftLightningPayload(buf.readVarInt());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
