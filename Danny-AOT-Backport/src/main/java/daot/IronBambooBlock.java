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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager.Builder;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

public class IronBambooBlock extends Block implements Fertilizable {
   public static final IntProperty AGE = Properties.AGE_1;
   public static final EnumProperty<BambooLeaves> LEAVES = Properties.BAMBOO_LEAVES;
   public static final IntProperty STAGE = Properties.STAGE;
   protected static final VoxelShape SMALL_SHAPE = Block.createCuboidShape(5.0, 0.0, 5.0, 11.0, 16.0, 11.0);
   protected static final VoxelShape LARGE_SHAPE = Block.createCuboidShape(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
   protected static final VoxelShape COLLISION_SHAPE = Block.createCuboidShape(6.5, 0.0, 6.5, 9.5, 16.0, 9.5);
   public static final int MAX_HEIGHT = 32;

   public IronBambooBlock() {
      super(
         Settings.create()
            .mapColor(MapColor.DARK_GREEN)
            .solid()
            .ticksRandomly()
            .breakInstantly()
            .strength(1.0F)
            .sounds(BlockSoundGroup.BAMBOO)
            .nonOpaque()
            .dynamicBounds()
            .offset(OffsetType.XZ)
            .burnable()
            .pistonBehavior(PistonBehavior.DESTROY)
      );
      this.setDefaultState(this.stateManager.getDefaultState().with(AGE, 0).with(LEAVES, BambooLeaves.NONE).with(STAGE, 0));
   }


   @Override
   protected void appendProperties(Builder<Block, BlockState> builder) {
      builder.add(AGE, LEAVES, STAGE);
   }

   @Override
   public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
      VoxelShape shape = state.get(LEAVES) == BambooLeaves.LARGE ? LARGE_SHAPE : SMALL_SHAPE;
      Vec3d offset = state.getModelOffset(world, pos);
      return shape.offset(offset.x, 0.0, offset.z);
   }

   @Override
   public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
      Vec3d offset = state.getModelOffset(world, pos);
      return COLLISION_SHAPE.offset(offset.x, 0.0, offset.z);
   }

   @Override
   public boolean canPathfindThrough(BlockState state, BlockView world, BlockPos pos, NavigationType type) {
      return false;
   }

   @Nullable
   @Override
   public BlockState getPlacementState(ItemPlacementContext ctx) {
      BlockState below = ctx.getWorld().getBlockState(ctx.getBlockPos().down());
      if (!below.isIn(BlockTags.BAMBOO_PLANTABLE_ON) && !below.isOf(this) && !below.isOf(DannysAot.IRON_BAMBOO_SAPLING)) {
         return null;
      } else if (below.isOf(this)) {
         int stage = below.get(STAGE);
         return this.getDefaultState().with(LEAVES, BambooLeaves.NONE).with(STAGE, stage);
      } else {
         return below.isOf(DannysAot.IRON_BAMBOO_SAPLING) ? this.getDefaultState().with(LEAVES, BambooLeaves.SMALL).with(STAGE, 0) : this.getDefaultState();
      }
   }

   @Override
   public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
      if (!state.canPlaceAt(world, pos)) {
         world.breakBlock(pos, true);
      }
   }

   @Override
   public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
      super.onBlockAdded(state, world, pos, oldState, notify);
      BlockPos belowPos = pos.down();
      BlockState belowState = world.getBlockState(belowPos);
      if (belowState.isOf(DannysAot.IRON_BAMBOO_SAPLING)) {
         world.setBlockState(belowPos, this.getDefaultState().with(LEAVES, BambooLeaves.NONE).with(STAGE, 0).with(AGE, 0), 3);
      }
   }

   @Override
   public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
      if (state.get(STAGE) == 0 && random.nextInt(3) == 0 && world.isAir(pos.up()) && world.getBaseLightLevel(pos.up(), 0) >= 9) {
         int height = this.getHeightAboveUpToMax(world, pos) + this.getHeightBelowUpToMax(world, pos) + 1;
         if (height < 32) {
            this.growBamboo(state, world, pos, random, height);
         }
      }
   }

   @Override
   public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
      BlockState below = world.getBlockState(pos.down());
      return below.isOf(this) || below.isOf(DannysAot.IRON_BAMBOO_SAPLING) || below.isIn(BlockTags.BAMBOO_PLANTABLE_ON);
   }

   @Override
   public BlockState getStateForNeighborUpdate(
      BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos
   ) {
      if (!state.canPlaceAt(world, pos)) {
         world.scheduleBlockTick(pos, this, 1);
      }

      return direction == Direction.UP && neighborState.isOf(this)
         ? state.with(LEAVES, this.getLeafState(world, pos))
         : super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
   }

   @Override
   public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
      return player.getMainHandStack().getItem() instanceof SwordItem ? 1.0F : super.calcBlockBreakingDelta(state, player, world, pos);
   }

   @Override
   public ItemStack getPickStack(net.minecraft.world.BlockView world, BlockPos pos, BlockState state) {
      return new ItemStack(DannysAot.IRON_BAMBOO_ITEM);
   }

   @Override
   public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state, boolean isClient) {
      int heightAbove = this.getHeightAboveUpToMax(world, pos);
      int heightBelow = this.getHeightBelowUpToMax(world, pos);
      int totalHeight = heightAbove + heightBelow + 1;
      return totalHeight < 32 && world.getBlockState(pos.up(heightAbove)).get(STAGE) != 1;
   }

   @Override
   public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
      return true;
   }

   @Override
   public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
      int heightAbove = this.getHeightAboveUpToMax(world, pos);
      int heightBelow = this.getHeightBelowUpToMax(world, pos);
      int totalHeight = heightAbove + heightBelow + 1;
      int growth = 1 + random.nextInt(2);

      for (int i = 0; i < growth; i++) {
         BlockPos topPos = pos.up(heightAbove);
         BlockState topState = world.getBlockState(topPos);
         if (totalHeight >= 32 || topState.get(STAGE) == 1 || !world.isAir(topPos.up())) {
            return;
         }

         this.growBamboo(topState, world, topPos, random, totalHeight);
         heightAbove++;
         totalHeight++;
      }
   }

   protected void growBamboo(BlockState state, ServerWorld level, BlockPos pos, Random random, int height) {
      BlockState below = level.getBlockState(pos.down());
      BlockPos twoBelow = pos.down(2);
      BlockState stateTwoBelow = level.getBlockState(twoBelow);
      BambooLeaves leaves = BambooLeaves.NONE;
      if (height >= 1) {
         if (!below.isOf(this) || below.get(LEAVES) == BambooLeaves.NONE) {
            leaves = BambooLeaves.SMALL;
         } else if (below.isOf(this) && below.get(LEAVES) != BambooLeaves.NONE) {
            leaves = BambooLeaves.LARGE;
            if (stateTwoBelow.isOf(this)) {
               level.setBlockState(pos.down(), below.with(LEAVES, BambooLeaves.SMALL), 3);
               level.setBlockState(twoBelow, stateTwoBelow.with(LEAVES, BambooLeaves.NONE), 3);
            }
         }
      }

      int stage = height < 11 && state.get(STAGE) != 1 ? 0 : 1;
      if (height >= 5 && random.nextFloat() < 0.25F) {
         stage = 1;
      }

      level.setBlockState(pos.up(), this.getDefaultState().with(LEAVES, leaves).with(STAGE, stage), 3);
   }

   protected BambooLeaves getLeafState(WorldAccess level, BlockPos pos) {
      BlockState above = level.getBlockState(pos.up());
      BlockState twoAbove = level.getBlockState(pos.up(2));
      if (!above.isOf(this)) {
         return BambooLeaves.LARGE;
      } else {
         return !twoAbove.isOf(this) ? BambooLeaves.SMALL : BambooLeaves.NONE;
      }
   }

   protected int getHeightAboveUpToMax(BlockView level, BlockPos pos) {
      int height = 0;

      while (height < 32 && level.getBlockState(pos.up(height + 1)).isOf(this)) {
         height++;
      }

      return height;
   }

   protected int getHeightBelowUpToMax(BlockView level, BlockPos pos) {
      int height = 0;

      while (height < 32 && level.getBlockState(pos.down(height + 1)).isOf(this)) {
         height++;
      }

      return height;
   }
}
