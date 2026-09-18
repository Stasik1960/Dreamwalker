package daot;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

public class DarkPathSandBlock extends FallingBlock {

   public DarkPathSandBlock() {
      super(Settings.create().strength(0.5F).sounds(BlockSoundGroup.SAND).mapColor(MapColor.BLUE));
   }


   @Override
   public int getColor(BlockState state, BlockView world, BlockPos pos) {
      return 7048100;
   }
}
