package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record BladeAOEPayload(int attackType, int animAction, int chargeTimeTicks, float playerSpeed) implements CustomPayload {
   public static final int SPIN_SLASH = 0;
   public static final int UP_SLASH = 1;
   public static final int DOWN_SLASH = 2;
   public static final Id<BladeAOEPayload> TYPE = new Id<>(new Identifier("dannys-aot", "blade_aoe"));
   public static final PacketCodec<PacketByteBuf, BladeAOEPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, BladeAOEPayload>() {
      public BladeAOEPayload decode(PacketByteBuf buf) {
         return new BladeAOEPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat());
      }

      public void encode(PacketByteBuf buf, BladeAOEPayload payload) {
         buf.writeVarInt(payload.attackType());
         buf.writeVarInt(payload.animAction());
         buf.writeVarInt(payload.chargeTimeTicks());
         buf.writeFloat(payload.playerSpeed());
      }
   };

   public BladeAOEPayload(int attackType, int animAction) {
      this(attackType, animAction, 0, 0.0F);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
