package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record SadTitanSprintPayload(boolean sprinting) implements CustomPayload {
   public static final Id<SadTitanSprintPayload> TYPE = new Id<>(new Identifier("dannys-aot", "sad_titan_sprint"));
   public static final PacketCodec<PacketByteBuf, SadTitanSprintPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, SadTitanSprintPayload>() {
      public SadTitanSprintPayload decode(PacketByteBuf buf) {
         return new SadTitanSprintPayload(buf.readBoolean());
      }

      public void encode(PacketByteBuf buf, SadTitanSprintPayload payload) {
         buf.writeBoolean(payload.sprinting);
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
