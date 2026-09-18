package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record BloodmoonMusicPayload(int trackIndex) implements CustomPayload {
   public static final Id<BloodmoonMusicPayload> TYPE = new Id<>(new Identifier("dannys-aot", "bloodmoon_music"));
   public static final PacketCodec<PacketByteBuf, BloodmoonMusicPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, BloodmoonMusicPayload>() {
      public BloodmoonMusicPayload decode(PacketByteBuf buf) {
         return new BloodmoonMusicPayload(buf.readVarInt());
      }

      public void encode(PacketByteBuf buf, BloodmoonMusicPayload payload) {
         buf.writeVarInt(payload.trackIndex());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
