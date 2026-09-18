package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.PacketCodecs;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record EffectPayload(String effectType, double x, double y, double z, float scale) implements CustomPayload {
   public static final String EFFECT_BLOOD = "blood";
   public static final String EFFECT_ROAR = "roar";
   public static final String EFFECT_BLADE_SWING = "blade_swing";
   public static final String EFFECT_ROYAL_SHOUT = "royal_shout";
   public static final String EFFECT_COMMAND_STOP = "command_stop";
   public static final String EFFECT_COMMAND_CONTINUE = "command_continue";
   public static final String EFFECT_THUNDER_SPEAR_EXPLODE = "thunder_spear_explode";
   public static final String EFFECT_THUNDER_SPEAR_LODGE = "thunder_spear_lodge";
   public static final String EFFECT_WIND = "wind";
   public static final String EFFECT_WIND_STOP = "wind_stop";
   public static final String EFFECT_TELEPORT = "teleport";
   public static final Id<EffectPayload> TYPE = new Id<>(new Identifier("dannys-aot", "effect"));
   public static final PacketCodec<PacketByteBuf, EffectPayload> STREAM_CODEC = PacketCodec.tuple(
      PacketCodecs.STRING,
      EffectPayload::effectType,
      PacketCodecs.DOUBLE,
      EffectPayload::x,
      PacketCodecs.DOUBLE,
      EffectPayload::y,
      PacketCodecs.DOUBLE,
      EffectPayload::z,
      PacketCodecs.FLOAT,
      EffectPayload::scale,
      EffectPayload::new
   );

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
