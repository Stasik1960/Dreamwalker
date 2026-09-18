package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record FlareGunLoadPayload() implements CustomPayload {
   public static final Id<FlareGunLoadPayload> TYPE = new Id<>(new Identifier("dannys-aot", "flare_gun_load"));
   public static final PacketCodec<PacketByteBuf, FlareGunLoadPayload> STREAM_CODEC = PacketCodec.unit(new FlareGunLoadPayload());

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
