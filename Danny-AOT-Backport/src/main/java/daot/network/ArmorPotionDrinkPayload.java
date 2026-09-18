package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ArmorPotionDrinkPayload(boolean hasHardening, boolean justDrank) implements CustomPayload {
   public static final Id<ArmorPotionDrinkPayload> TYPE = new Id<>(new Identifier("dannys-aot", "armor_potion_drink"));
   public static final PacketCodec<PacketByteBuf, ArmorPotionDrinkPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, ArmorPotionDrinkPayload>() {
      public ArmorPotionDrinkPayload decode(PacketByteBuf buf) {
         return new ArmorPotionDrinkPayload(buf.readBoolean(), buf.readBoolean());
      }

      public void encode(PacketByteBuf buf, ArmorPotionDrinkPayload payload) {
         buf.writeBoolean(payload.hasHardening());
         buf.writeBoolean(payload.justDrank());
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
