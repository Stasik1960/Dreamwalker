package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record AtrainPlaySpeedSoundPayload() implements CustomPayload {
   public static final Id<AtrainPlaySpeedSoundPayload> TYPE = new Id<>(new Identifier("dannys-aot", "atrain_play_speed_sound"));
   public static final PacketCodec<PacketByteBuf, AtrainPlaySpeedSoundPayload> STREAM_CODEC = PacketCodec.unit(new AtrainPlaySpeedSoundPayload());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
