package daot;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class IceBurstStoneBlock extends Block {

   public IceBurstStoneBlock() {
      super(Settings.create().strength(1.5F, 6.0F).sounds(BlockSoundGroup.AMETHYST_BLOCK).mapColor(MapColor.PALE_PURPLE).luminance(state -> 15));
   }


   @Override
   public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
      if (random.nextInt(5) == 0) {
         Direction direction = Direction.random(random);
         BlockPos adjacentPos = pos.offset(direction);
         if (!world.getBlockState(adjacentPos).isOpaqueFullCube(world, adjacentPos)) {
            double x;
            double y;
            double z;
            if (direction.getAxis() == Axis.X) {
               x = pos.getX() + (direction == Direction.EAST ? 1.01 : -0.01);
               y = pos.getY() + random.nextDouble();
               z = pos.getZ() + random.nextDouble();
            } else if (direction.getAxis() == Axis.Y) {
               x = pos.getX() + random.nextDouble();
               y = pos.getY() + (direction == Direction.UP ? 1.01 : -0.01);
               z = pos.getZ() + random.nextDouble();
            } else {
               x = pos.getX() + random.nextDouble();
               y = pos.getY() + random.nextDouble();
               z = pos.getZ() + (direction == Direction.SOUTH ? 1.01 : -0.01);
            }

            double vx = direction.getOffsetX() * 0.02;
            double vy = direction.getOffsetY() * 0.02 + 0.01;
            double vz = direction.getOffsetZ() * 0.02;
            world.addParticle(daot.compat.BackportEffects.SMALL_GUST, x, y, z, vx, vy, vz);
         }
      }
   }
}
