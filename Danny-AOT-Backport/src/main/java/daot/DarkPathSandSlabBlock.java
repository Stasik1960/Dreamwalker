package daot;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.MapColor;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.sound.BlockSoundGroup;

public class DarkPathSandSlabBlock extends SlabBlock {

   public DarkPathSandSlabBlock() {
      super(Settings.create().mapColor(MapColor.GRAY).strength(0.5F).sounds(BlockSoundGroup.SAND));
   }

}
