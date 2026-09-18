package daot;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.sound.BlockSoundGroup;

public class BlockOfIceburst extends Block {

   public BlockOfIceburst() {
      super(Settings.create().strength(1.5F, 6.0F).sounds(BlockSoundGroup.AMETHYST_BLOCK).mapColor(MapColor.PALE_PURPLE).luminance(state -> 15));
   }

}
