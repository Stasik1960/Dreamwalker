package daot;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.MapColor;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.sound.BlockSoundGroup;

public class PathSandSlabBlock extends SlabBlock {

   public PathSandSlabBlock() {
      super(Settings.create().mapColor(MapColor.LIGHT_BLUE).strength(0.5F).sounds(BlockSoundGroup.SAND));
   }

}
