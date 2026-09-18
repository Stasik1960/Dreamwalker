package daot;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

public class PathSandBlock extends FallingBlock {

   public PathSandBlock() {
      super(Settings.create().strength(0.5F).sounds(BlockSoundGroup.SAND).mapColor(MapColor.PALE_YELLOW));
   }


   @Override
   public int getColor(BlockState state, BlockView world, BlockPos pos) {
      return 13952753;
   }
}
