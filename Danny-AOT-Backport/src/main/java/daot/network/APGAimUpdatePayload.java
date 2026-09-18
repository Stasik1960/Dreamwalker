package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record APGAimUpdatePayload(boolean mainAiming, boolean offAiming) implements CustomPayload {
   public static final Id<APGAimUpdatePayload> TYPE = new Id<>(new Identifier("dannys-aot", "apg_aim_update"));
   public static final PacketCodec<PacketByteBuf, APGAimUpdatePayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, APGAimUpdatePayload>() {
      public APGAimUpdatePayload decode(PacketByteBuf buf) {
         return new APGAimUpdatePayload(buf.readBoolean(), buf.readBoolean());
      }

      public void encode(PacketByteBuf buf, APGAimUpdatePayload payload) {
         buf.writeBoolean(payload.mainAiming());
         buf.writeBoolean(payload.offAiming());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
