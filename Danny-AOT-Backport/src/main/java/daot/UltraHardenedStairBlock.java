package daot;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.sound.BlockSoundGroup;

public class UltraHardenedStairBlock extends StairsBlock {

   public UltraHardenedStairBlock(Block base) {
      super(
         base.getDefaultState(), Settings.create().strength(50.0F, 1200.0F).sounds(BlockSoundGroup.GLASS).mapColor(MapColor.BLACK).requiresTool().nonOpaque()
      );
   }

}
