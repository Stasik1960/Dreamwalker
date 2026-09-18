package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record FounderHumanAbilityPayload(int abilityNumber) implements CustomPayload {
   public static final Id<FounderHumanAbilityPayload> TYPE = new Id<>(new Identifier("dannys-aot", "founder_human_ability"));
   public static final PacketCodec<PacketByteBuf, FounderHumanAbilityPayload> STREAM_CODEC = PacketCodec.ofStatic(
      (buf, payload) -> buf.writeInt(payload.abilityNumber), buf -> new FounderHumanAbilityPayload(buf.readInt())
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
