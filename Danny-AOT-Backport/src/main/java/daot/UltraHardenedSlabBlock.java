package daot;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.MapColor;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.sound.BlockSoundGroup;

public class UltraHardenedSlabBlock extends SlabBlock {

   public UltraHardenedSlabBlock() {
      super(Settings.create().strength(50.0F, 1200.0F).sounds(BlockSoundGroup.GLASS).mapColor(MapColor.BLACK).requiresTool().nonOpaque());
   }

}
