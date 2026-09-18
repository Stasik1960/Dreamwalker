package daot;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextParameterSet.Builder;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class BurstingIceBurstStoneBlock extends Block {
   private static final Direction[] DIRECTIONS = Direction.values();

   public BurstingIceBurstStoneBlock() {
      super(Settings.create().strength(1.5F, 6.0F).sounds(BlockSoundGroup.AMETHYST_BLOCK).mapColor(MapColor.PALE_PURPLE).ticksRandomly().luminance(state -> 15));
   }


   private static boolean isIronPickaxeOrBetter(ItemStack tool) {
      if (!(tool.getItem() instanceof PickaxeItem pickaxe)) {
         return false;
      } else {
         ToolMaterial tier = pickaxe.getMaterial();
         return tier == ToolMaterials.IRON || tier == ToolMaterials.DIAMOND || tier == ToolMaterials.NETHERITE;
      }
   }

   @Override
   public List<ItemStack> getDroppedStacks(BlockState state, Builder builder) {
      ItemStack tool = builder.getOptional(LootContextParameters.TOOL);
      return tool != null && isIronPickaxeOrBetter(tool) ? List.of(new ItemStack(DannysAot.BURSTING_ICE_BURST_STONE)) : List.of();
   }

   @Override
   public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
      float hardness = state.getHardness(world, pos);
      if (hardness == -1.0F) {
         return 0.0F;
      } else {
         float speed = 1.0F;
         boolean correctTool = false;
         if (player.getMainHandStack().getItem() instanceof PickaxeItem pickaxe) {
            speed = pickaxe.getMaterial().getMiningSpeedMultiplier();
            correctTool = isIronPickaxeOrBetter(player.getMainHandStack());
         }

         int divisor = correctTool ? 30 : 100;
         return speed / hardness / divisor;
      }
   }

   @Override
   public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
      if (random.nextInt(2) == 0) {
         Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
         BlockPos adjacentPos = pos.offset(direction);
         BlockState adjacentState = world.getBlockState(adjacentPos);
         Block adjacentBlock = adjacentState.getBlock();
         if (canGrowIn(adjacentState)) {
            world.setBlockState(
               adjacentPos,
               DannysAot.SMALL_ICE_BURST_SHARD
                  .getDefaultState()
                  .with(AmethystClusterBlock.FACING, direction)
                  .with(AmethystClusterBlock.WATERLOGGED, adjacentState.getFluidState().getFluid() == Fluids.WATER)
            );
         } else if (adjacentBlock == DannysAot.SMALL_ICE_BURST_SHARD && adjacentState.get(AmethystClusterBlock.FACING) == direction) {
            world.setBlockState(
               adjacentPos,
               DannysAot.MEDIUM_ICE_BURST_SHARD
                  .getDefaultState()
                  .with(AmethystClusterBlock.FACING, direction)
                  .with(AmethystClusterBlock.WATERLOGGED, adjacentState.get(AmethystClusterBlock.WATERLOGGED))
            );
         } else if (adjacentBlock == DannysAot.MEDIUM_ICE_BURST_SHARD && adjacentState.get(AmethystClusterBlock.FACING) == direction) {
            world.setBlockState(
               adjacentPos,
               DannysAot.LARGE_ICE_BURST_SHARD
                  .getDefaultState()
                  .with(AmethystClusterBlock.FACING, direction)
                  .with(AmethystClusterBlock.WATERLOGGED, adjacentState.get(AmethystClusterBlock.WATERLOGGED))
            );
         } else if (adjacentBlock == DannysAot.LARGE_ICE_BURST_SHARD && adjacentState.get(AmethystClusterBlock.FACING) == direction) {
            world.setBlockState(
               adjacentPos,
               DannysAot.ICE_BURST_SHARD_CLUSTER
                  .getDefaultState()
                  .with(AmethystClusterBlock.FACING, direction)
                  .with(AmethystClusterBlock.WATERLOGGED, adjacentState.get(AmethystClusterBlock.WATERLOGGED))
            );
         }
      }
   }

   private static boolean canGrowIn(BlockState state) {
      return state.isAir() || state.isOf(Blocks.WATER) && state.getFluidState().getLevel() == 8;
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
