package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record WeatherAffectsODMPayload(boolean enabled) implements CustomPayload {
   public static final Id<WeatherAffectsODMPayload> TYPE = new Id<>(new Identifier("dannys-aot", "weather_affects_odm"));
   public static final PacketCodec<PacketByteBuf, WeatherAffectsODMPayload> STREAM_CODEC = PacketCodec.ofStatic(
      WeatherAffectsODMPayload::write, WeatherAffectsODMPayload::read
   );

   private static WeatherAffectsODMPayload read(PacketByteBuf buf) {
      return new WeatherAffectsODMPayload(buf.readBoolean());
   }

   private static void write(PacketByteBuf buf, WeatherAffectsODMPayload payload) {
      buf.writeBoolean(payload.enabled);
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
