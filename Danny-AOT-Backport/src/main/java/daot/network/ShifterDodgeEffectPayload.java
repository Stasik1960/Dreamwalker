package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ShifterDodgeEffectPayload(int titanEntityId, float dirX, float dirZ) implements CustomPayload {
   public static final Id<ShifterDodgeEffectPayload> TYPE = new Id<>(new Identifier("dannys-aot", "shifter_dodge_effect"));
   public static final PacketCodec<PacketByteBuf, ShifterDodgeEffectPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, ShifterDodgeEffectPayload>() {
      public ShifterDodgeEffectPayload decode(PacketByteBuf buf) {
         return new ShifterDodgeEffectPayload(buf.readInt(), buf.readFloat(), buf.readFloat());
      }

      public void encode(PacketByteBuf buf, ShifterDodgeEffectPayload payload) {
         buf.writeInt(payload.titanEntityId());
         buf.writeFloat(payload.dirX());
         buf.writeFloat(payload.dirZ());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
