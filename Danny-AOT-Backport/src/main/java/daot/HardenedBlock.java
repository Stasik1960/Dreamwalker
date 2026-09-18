package daot;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.MapColor;
import net.minecraft.block.TransparentBlock;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.sound.BlockSoundGroup;

public class HardenedBlock extends TransparentBlock {

   public HardenedBlock() {
      super(Settings.create().strength(50.0F, 1200.0F).sounds(BlockSoundGroup.GLASS).mapColor(MapColor.BLACK).requiresTool().nonOpaque());
   }

}
