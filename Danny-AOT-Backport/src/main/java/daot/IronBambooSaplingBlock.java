package daot;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.MapColor;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.AbstractBlock.OffsetType;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.block.enums.BambooLeaves;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

public class IronBambooSaplingBlock extends Block implements Fertilizable {
   protected static final VoxelShape SAPLING_SHAPE = Block.createCuboidShape(4.0, 0.0, 4.0, 12.0, 12.0, 12.0);

   public IronBambooSaplingBlock() {
      super(
         Settings.create()
            .mapColor(MapColor.OAK_TAN)
            .solid()
            .ticksRandomly()
            .breakInstantly()
            .noCollision()
            .strength(1.0F)
            .sounds(BlockSoundGroup.BAMBOO_SAPLING)
            .offset(OffsetType.XZ)
            .burnable()
            .pistonBehavior(PistonBehavior.DESTROY)
      );
   }


   @Override
   public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
      Vec3d offset = state.getModelOffset(world, pos);
      return SAPLING_SHAPE.offset(offset.x, offset.y, offset.z);
   }

   @Override
   public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
      return VoxelShapes.empty();
   }

   @Override
   public boolean canPathfindThrough(BlockState state, BlockView world, BlockPos pos, NavigationType type) {
      return true;
   }

   @Override
   public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
      if (random.nextInt(3) == 0 && world.isAir(pos.up()) && world.getBaseLightLevel(pos.up(), 0) >= 9) {
         this.growBamboo(world, pos);
      }
   }

   @Override
   public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
      return world.getBlockState(pos.down()).isIn(BlockTags.BAMBOO_PLANTABLE_ON);
   }

   @Override
   public BlockState getStateForNeighborUpdate(
      BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos
   ) {
      if (!state.canPlaceAt(world, pos)) {
         world.scheduleBlockTick(pos, this, 1);
      }

      return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
   }

   @Override
   public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
      if (!state.canPlaceAt(world, pos)) {
         world.breakBlock(pos, true);
      }
   }

   @Override
   public ItemStack getPickStack(net.minecraft.world.BlockView world, BlockPos pos, BlockState state) {
      return new ItemStack(DannysAot.IRON_BAMBOO_ITEM);
   }

   @Override
   public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state, boolean isClient) {
      return world.getBlockState(pos.up()).isAir();
   }

   @Override
   public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
      return true;
   }

   @Override
   public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
      this.growBamboo(world, pos);
   }

   protected void growBamboo(ServerWorld level, BlockPos pos) {
      level.setBlockState(pos, DannysAot.IRON_BAMBOO.getDefaultState().with(IronBambooBlock.LEAVES, BambooLeaves.NONE).with(IronBambooBlock.STAGE, 0), 3);
      level.setBlockState(pos.up(), DannysAot.IRON_BAMBOO.getDefaultState().with(IronBambooBlock.LEAVES, BambooLeaves.SMALL).with(IronBambooBlock.STAGE, 0), 3);
   }
}
