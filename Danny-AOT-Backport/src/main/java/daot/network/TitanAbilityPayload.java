package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record TitanAbilityPayload(int abilityNumber) implements CustomPayload {
   public static final Id<TitanAbilityPayload> TYPE = new Id<>(new Identifier("dannys-aot", "titan_ability"));
   public static final PacketCodec<PacketByteBuf, TitanAbilityPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, payload) -> buf.writeInt(payload.abilityNumber), buf -> new TitanAbilityPayload(buf.readInt())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
