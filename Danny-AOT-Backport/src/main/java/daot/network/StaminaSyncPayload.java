package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record StaminaSyncPayload(float stamina, float maxStamina, boolean hasBeast, boolean hasFounding) implements CustomPayload {
   public static final Id<StaminaSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "stamina_sync"));
   public static final PacketCodec<PacketByteBuf, StaminaSyncPayload> STREAM_CODEC = PacketCodec.ofStatic(
      StaminaSyncPayload::encode, StaminaSyncPayload::decode
   );

   public StaminaSyncPayload(float stamina, float maxStamina) {
      this(stamina, maxStamina, false, false);
   }

   public StaminaSyncPayload(float stamina, float maxStamina, boolean hasBeast) {
      this(stamina, maxStamina, hasBeast, false);
   }

   private static void encode(PacketByteBuf buf, StaminaSyncPayload payload) {
      buf.writeFloat(payload.stamina);
      buf.writeFloat(payload.maxStamina);
      buf.writeBoolean(payload.hasBeast);
      buf.writeBoolean(payload.hasFounding);
   }

   private static StaminaSyncPayload decode(PacketByteBuf buf) {
      return new StaminaSyncPayload(buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
