package daot;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.sound.BlockSoundGroup;

public class BambooLeavesBlock extends LeavesBlock {

   public BambooLeavesBlock() {
      super(
         Settings.create()
            .mapColor(MapColor.DARK_GREEN)
            .strength(0.2F)
            .ticksRandomly()
            .sounds(BlockSoundGroup.GRASS)
            .nonOpaque()
            .allowsSpawning((state, level, pos, type) -> false)
            .suffocates((state, level, pos) -> false)
            .blockVision((state, level, pos) -> false)
            .burnable()
            .pistonBehavior(PistonBehavior.DESTROY)
            .solidBlock((state, level, pos) -> false)
      );
   }

}
