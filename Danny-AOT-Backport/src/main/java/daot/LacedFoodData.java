package daot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.PacketCodecs;

public record LacedFoodData(String sourceName, boolean isRoyal) {
   public static final Codec<LacedFoodData> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.STRING.fieldOf("source_name").forGetter(LacedFoodData::sourceName),
            Codec.BOOL.optionalFieldOf("is_royal", false).forGetter(LacedFoodData::isRoyal)
         )
         .apply(instance, LacedFoodData::new)
   );
   public static final PacketCodec<PacketByteBuf, LacedFoodData> STREAM_CODEC = PacketCodec.tuple(
      PacketCodecs.STRING, LacedFoodData::sourceName, PacketCodecs.BOOL, LacedFoodData::isRoyal, LacedFoodData::new
   );
}
