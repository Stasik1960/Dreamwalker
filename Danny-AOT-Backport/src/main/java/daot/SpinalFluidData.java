package daot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.PacketCodecs;

public record SpinalFluidData(String sourceName, boolean isRoyal) {
   public static final SpinalFluidData DEFAULT = new SpinalFluidData("", false);
   public static final Codec<SpinalFluidData> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.STRING.fieldOf("source_name").forGetter(SpinalFluidData::sourceName),
            Codec.BOOL.optionalFieldOf("is_royal", false).forGetter(SpinalFluidData::isRoyal)
         )
         .apply(instance, SpinalFluidData::new)
   );
   public static final PacketCodec<PacketByteBuf, SpinalFluidData> STREAM_CODEC = PacketCodec.tuple(
      PacketCodecs.STRING, SpinalFluidData::sourceName, PacketCodecs.BOOL, SpinalFluidData::isRoyal, SpinalFluidData::new
   );

   public SpinalFluidData(String sourceName) {
      this(sourceName, false);
   }

   public boolean isGeneric() {
      return this.sourceName.isEmpty();
   }

   public String getDisplayName() {
      return this.isGeneric() ? "Titan Spinal Fluid" : this.sourceName + "'s Spinal Fluid";
   }
}
