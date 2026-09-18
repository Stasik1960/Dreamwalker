package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ButcherTentaclePayload() implements CustomPayload {
   public static final Id<ButcherTentaclePayload> TYPE = new Id<>(new Identifier("dannys-aot", "butcher_tentacle"));
   public static final PacketCodec<PacketByteBuf, ButcherTentaclePayload> STREAM_CODEC = PacketCodec.unit(new ButcherTentaclePayload());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
