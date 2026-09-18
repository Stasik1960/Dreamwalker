package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record FlareGunCyclePayload() implements CustomPayload {
   public static final Id<FlareGunCyclePayload> TYPE = new Id<>(new Identifier("dannys-aot", "flare_gun_cycle"));
   public static final PacketCodec<PacketByteBuf, FlareGunCyclePayload> STREAM_CODEC = PacketCodec.unit(new FlareGunCyclePayload());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
