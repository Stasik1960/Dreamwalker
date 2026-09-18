package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record BladeBlockBrokenPayload() implements CustomPayload {
   public static final Id<BladeBlockBrokenPayload> TYPE = new Id<>(new Identifier("dannys-aot", "blade_block_broken"));
   public static final PacketCodec<PacketByteBuf, BladeBlockBrokenPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, payload) -> {}, buf -> new BladeBlockBrokenPayload()
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
